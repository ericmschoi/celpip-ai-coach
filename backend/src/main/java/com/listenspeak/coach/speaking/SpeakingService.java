package com.listenspeak.coach.speaking;

import com.listenspeak.coach.platform.config.AppProperties;
import com.listenspeak.coach.platform.config.AppProperties.ContentMode;
import com.listenspeak.coach.platform.limits.LimitedAction;
import com.listenspeak.coach.platform.limits.UsageLimiter;
import com.listenspeak.coach.platform.media.MediaProcessingException;
import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.platform.web.ErrorCode;
import com.listenspeak.coach.speaking.SpeakingTaskCatalog.SpeakingTask;
import com.listenspeak.coach.speaking.api.SpeakingViews.EvaluationView;
import com.listenspeak.coach.speaking.api.SpeakingViews.PromptView;
import com.listenspeak.coach.speaking.domain.DeliveryMetrics;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation;
import com.listenspeak.coach.speaking.domain.SpeakingPrompt;
import com.listenspeak.coach.speaking.evaluation.RecordingAnalyzer;
import com.listenspeak.coach.speaking.evaluation.ScoreGuard;
import com.listenspeak.coach.speaking.evaluation.SpeechPresence;
import com.listenspeak.coach.speaking.evaluation.SpeakingScorer;
import com.listenspeak.coach.speaking.evaluation.Transcriber;
import com.listenspeak.coach.speaking.prompts.PromptGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Speaking use cases: hand out an original prompt, then transcribe, measure,
 * score, and store one recorded answer.
 *
 * <p>The recording itself is deleted as soon as evaluation finishes unless
 * retention is explicitly enabled. Only the transcript and the feedback are
 * kept.
 */
@Service
public class SpeakingService {

    private static final Logger log = LoggerFactory.getLogger(SpeakingService.class);

    /** Allowance over the task limit, for encoder padding and a slow stop click. */
    private static final double DURATION_GRACE_SECONDS = 15;

    private final List<PromptGenerator> promptGenerators;
    private final List<SpeakingScorer> scorers;
    private final Transcriber transcriber;
    private final RecordingAnalyzer analyzer;
    private final ScoreGuard scoreGuard;
    private final SpeakingRepository repository;
    private final UsageLimiter usageLimiter;
    private final AppProperties properties;
    private final Clock clock;

    public SpeakingService(
            List<PromptGenerator> promptGenerators,
            List<SpeakingScorer> scorers,
            Transcriber transcriber,
            RecordingAnalyzer analyzer,
            ScoreGuard scoreGuard,
            SpeakingRepository repository,
            UsageLimiter usageLimiter,
            AppProperties properties,
            Clock clock) {
        this.promptGenerators = List.copyOf(promptGenerators);
        this.scorers = List.copyOf(scorers);
        this.transcriber = transcriber;
        this.analyzer = analyzer;
        this.scoreGuard = scoreGuard;
        this.repository = repository;
        this.usageLimiter = usageLimiter;
        this.properties = properties;
        this.clock = clock;
    }

    public List<SpeakingTask> tasks() {
        return SpeakingTaskCatalog.all();
    }

    public PromptView createPrompt(String ownerId, int taskNumber) {
        SpeakingTask task = SpeakingTaskCatalog.require(taskNumber);
        PromptGenerator.Draft draft = promptGenerator().generate(task);

        Instant now = clock.instant();
        SpeakingPrompt prompt = new SpeakingPrompt(
                UUID.randomUUID(),
                ownerId,
                task.taskNumber(),
                task.title(),
                draft.situation(),
                draft.instruction(),
                draft.bullets(),
                (int) task.preparation().toSeconds(),
                (int) task.answer().toSeconds(),
                draft.sourceRef(),
                now,
                now.plus(properties.storage().exerciseTtl()));

        repository.savePrompt(prompt);
        log.info("Created speaking prompt id={} task={} source={}", prompt.id(), taskNumber, draft.sourceRef());

        return PromptView.of(prompt);
    }

    public EvaluationView evaluate(String ownerId, UUID promptId, MultipartFile recording) {
        SpeakingPrompt prompt = repository
                .findPromptByOwnerAndId(ownerId, promptId)
                .orElseThrow(() -> ApiException.notFound("Prompt"));

        SpeakingTask task = SpeakingTaskCatalog.require(prompt.taskNumber());

        // Validate the upload before spending anything on it.
        try (UploadedRecording upload = UploadedRecording.accept(recording, properties.speaking())) {
            RecordingAnalyzer.Measurements measurements = measure(upload);
            requireSensibleDuration(measurements, prompt);
            requireSpeech(measurements);

            usageLimiter.consume(ownerId, LimitedAction.SPEAKING_EVALUATION);

            String transcript = transcriber.transcribe(upload.path(), upload.filename()).trim();

            // A transcriber that can hear but returned nothing means the audio
            // held no recognisable speech. A transcriber that cannot hear at all
            // is a missing capability, not a silent user - and the difference
            // has to survive all the way to the screen.
            boolean transcriptAvailable = transcriber.producesRealTranscript();
            if (transcriptAvailable && transcript.isEmpty()) {
                throw ApiException.validation(
                        "No speech could be recognised in that recording. Check your microphone and try again.");
            }

            DeliveryMetrics metrics = DeliveryMetrics.of(
                    transcript,
                    measurements.durationSeconds(),
                    prompt.answerSeconds(),
                    measurements.silenceRatio(),
                    measurements.longestSilenceSeconds());

            SpeakingScorer.Assessment assessment = scoreGuard.validate(
                    scorer().score(task, promptText(prompt), transcript, transcriptAvailable, metrics));

            SpeakingEvaluation evaluation = new SpeakingEvaluation(
                    UUID.randomUUID(),
                    ownerId,
                    prompt.id(),
                    prompt.taskNumber(),
                    transcript,
                    transcriptAvailable,
                    metrics,
                    assessment.estimatedLevel(),
                    assessment.confidence(),
                    assessment.dimensions(),
                    assessment.strengths(),
                    assessment.improvements(),
                    assessment.corrections(),
                    assessment.sampleAnswer(),
                    assessment.nextDrill(),
                    assessment.sourceRef(),
                    clock.instant());

            repository.saveEvaluation(evaluation);

            // Shape and outcome only: never the transcript, never the audio.
            log.info(
                    "Evaluated speaking task={} level={} confidence={} words={} durationSeconds={} source={}",
                    prompt.taskNumber(),
                    evaluation.estimatedLevel(),
                    evaluation.confidence(),
                    metrics.wordCount(),
                    metrics.durationSeconds(),
                    assessment.sourceRef());

            return EvaluationView.of(evaluation);
        }
        // The temp recording is deleted here, on every path, by AutoCloseable.
    }

    public EvaluationView getEvaluation(String ownerId, UUID evaluationId) {
        return repository
                .findEvaluationByOwnerAndId(ownerId, evaluationId)
                .map(EvaluationView::of)
                .orElseThrow(() -> ApiException.notFound("Evaluation"));
    }

    private RecordingAnalyzer.Measurements measure(UploadedRecording upload) {
        try {
            return analyzer.analyze(upload.path());
        } catch (MediaProcessingException e) {
            throw new ApiException(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "That file could not be read as audio. Try recording again.",
                    e);
        }
    }

    /**
     * Refuses an empty recording outright. Without this, someone who pressed
     * stop immediately or had a muted microphone would receive an evaluation of
     * silence, which reads exactly like an evaluation of speech.
     */
    private void requireSpeech(RecordingAnalyzer.Measurements measurements) {
        if (!SpeechPresence.hasSpeech(measurements)) {
            throw ApiException.validation(
                    "That recording is silent. Check that your microphone is working, then record your answer again.");
        }
    }

    private void requireSensibleDuration(RecordingAnalyzer.Measurements measurements, SpeakingPrompt prompt) {
        if (measurements.durationSeconds() < 1) {
            throw ApiException.validation("That recording is too short to evaluate.");
        }
        if (measurements.durationSeconds() > prompt.answerSeconds() + DURATION_GRACE_SECONDS) {
            throw ApiException.validation(
                    "That recording is longer than the %d seconds this task allows."
                            .formatted(prompt.answerSeconds()));
        }
    }

    private static String promptText(SpeakingPrompt prompt) {
        return prompt.situation() + "\n\n" + prompt.instruction()
                + (prompt.bullets().isEmpty() ? "" : "\n- " + String.join("\n- ", prompt.bullets()));
    }

    private PromptGenerator promptGenerator() {
        return select(promptGenerators, PromptGenerator::mode);
    }

    private SpeakingScorer scorer() {
        return select(scorers, SpeakingScorer::mode);
    }

    private <T> T select(List<T> candidates, java.util.function.Function<T, ContentMode> modeOf) {
        return candidates.stream()
                .filter(candidate -> modeOf.apply(candidate) == properties.contentMode())
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.PROVIDER_NOT_CONFIGURED,
                        "Live AI evaluation is selected but not configured. Set OPENAI_API_KEY, "
                                + "or run with APP_CONTENT_MODE=SEED for demo feedback."));
    }
}
