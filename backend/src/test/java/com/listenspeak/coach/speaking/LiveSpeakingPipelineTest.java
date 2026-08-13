package com.listenspeak.coach.speaking;

import static org.assertj.core.api.Assertions.assertThat;

import com.listenspeak.coach.speaking.SpeakingTaskCatalog.SpeakingTask;
import com.listenspeak.coach.speaking.domain.DeliveryMetrics;
import com.listenspeak.coach.speaking.evaluation.RecordingAnalyzer;
import com.listenspeak.coach.speaking.evaluation.ScoreGuard;
import com.listenspeak.coach.speaking.evaluation.SpeakingScorer;
import com.listenspeak.coach.speaking.evaluation.TranscriptAccuracy;
import com.listenspeak.coach.speaking.domain.TimingMetrics;
import com.listenspeak.coach.speaking.evaluation.TimingAnalysis;
import com.listenspeak.coach.speaking.evaluation.TranscriptionResult;
import com.listenspeak.coach.speaking.evaluation.Transcriber;
import com.listenspeak.coach.speaking.evaluation.WordTimingAnalyzer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * The one controlled test that spends money.
 *
 * <p>It is disabled unless {@code LISTENSPEAK_LIVE_TEST=true} is set explicitly,
 * so neither CI nor an ordinary {@code make test} can trigger a paid call. It
 * needs a <strong>real human recording</strong> and a manually prepared
 * <strong>verbatim</strong> reference transcript: a synthesised recording would
 * be unrealistically clean and would prove nothing about filler retention.
 *
 * <pre>
 * export OPENAI_API_KEY=...
 * export LISTENSPEAK_LIVE_TEST=true
 * export LIVE_RECORDING=/path/to/answer.webm
 * export LIVE_REFERENCE=/path/to/answer-reference.txt
 * export LIVE_TASK=1
 * cd backend &amp;&amp; ./mvnw test -Dtest=LiveSpeakingPipelineTest
 * </pre>
 */
@SpringBootTest
@TestPropertySource(
        properties = {
            "app.content-mode=LIVE",
            "app.auth.mode=LOCAL_STUB",
            "app.storage.mode=LOCAL",
            "app.storage.local-path=./target/live-test-data"
        })
@EnabledIfEnvironmentVariable(named = "LISTENSPEAK_LIVE_TEST", matches = "true")
class LiveSpeakingPipelineTest {

    @Autowired
    private Transcriber transcriber;

    @Autowired
    private WordTimingAnalyzer timingAnalyzer;

    @Autowired
    private RecordingAnalyzer analyzer;

    @Autowired
    private ScoreGuard scoreGuard;

    @Autowired
    private java.util.List<SpeakingScorer> scorers;

    @Test
    void oneRealRecordingThroughTranscriptionAndScoring() throws Exception {
        Path recording = Path.of(required("LIVE_RECORDING"));
        Path reference = Path.of(required("LIVE_REFERENCE"));
        SpeakingTask task = SpeakingTaskCatalog.require(Integer.parseInt(System.getenv().getOrDefault("LIVE_TASK", "1")));

        assertThat(recording).as("recording file").exists();
        assertThat(reference).as("verbatim reference transcript").exists();

        String referenceText = Files.readString(reference).trim();
        assertThat(referenceText).as("reference must not be empty").isNotBlank();

        // --- 1. transcription ------------------------------------------------
        assertThat(transcriber.producesRealTranscript())
                .as("LIVE mode must use the real transcriber, not the seed stub")
                .isTrue();

        TranscriptionResult transcription = transcriber.transcribe(recording, recording.getFileName().toString());

        // --- 2. timing pass and local measurement ----------------------------
        TimingAnalysis timing = timingAnalyzer.analyze(recording, recording.getFileName().toString());
        RecordingAnalyzer.Measurements measurements = analyzer.analyze(recording);
        DeliveryMetrics metrics = DeliveryMetrics.of(
                transcription.text(),
                measurements.durationSeconds(),
                (int) task.answer().toSeconds(),
                measurements.silenceRatio(),
                measurements.longestSilenceSeconds(),
                TimingMetrics.from(timing));

        // --- 3. accuracy against the reference -------------------------------
        TranscriptAccuracy.Report accuracy = TranscriptAccuracy.compare(referenceText, transcription.text());

        // --- 4. scoring -------------------------------------------------------
        SpeakingScorer scorer = scorers.stream()
                .filter(candidate -> candidate.mode() == com.listenspeak.coach.platform.config.AppProperties.ContentMode.LIVE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No LIVE scorer is registered; is OPENAI_API_KEY set?"));

        long scoringStartedAt = System.nanoTime();
        SpeakingScorer.Assessment assessment = scoreGuard.validate(
                scorer.score(task, "Live test of task " + task.taskNumber(), transcription.text(), true, metrics));
        long scoringMillis = (System.nanoTime() - scoringStartedAt) / 1_000_000;

        report(task, transcription, timing, accuracy, metrics, assessment, scoringMillis);

        // --- assertions: the pipeline genuinely worked ------------------------
        assertThat(transcription.text()).as("1. recognised transcript").isNotBlank();
        assertThat(accuracy.wordErrorRate()).as("7. normalized word error rate").isLessThan(0.35);
        assertThat(assessment.dimensions()).as("all four dimensions").hasSize(4);
        assertThat(assessment.dimensions()).allSatisfy(dimension -> {
            assertThat(dimension.score()).as("live scoring assesses every dimension").isNotNull();
            assertThat(dimension.evidence()).isNotBlank();
        });
        assertThat(assessment.estimatedLevel()).as("live estimate").isBetween(1, 12);
        assertThat(assessment.sampleAnswer()).isNotBlank();
    }

    private void report(
            SpeakingTask task,
            TranscriptionResult transcription,
            TimingAnalysis timing,
            TranscriptAccuracy.Report accuracy,
            DeliveryMetrics metrics,
            SpeakingScorer.Assessment assessment,
            long scoringMillis) {

        StringBuilder out = new StringBuilder("\n");
        out.append("=".repeat(72)).append("\n");
        out.append("LIVE SPEAKING PIPELINE REPORT - task ").append(task.taskNumber())
                .append(" (").append(task.title()).append(")\n");
        out.append("=".repeat(72)).append("\n\n");

        out.append("1. RECOGNISED TRANSCRIPT\n").append(transcription.text()).append("\n\n");

        out.append("2. MISSING OR INCORRECTLY RECOGNISED WORDS\n");
        out.append("   missing from result : ").append(accuracy.missingWords()).append("\n");
        out.append("   not in reference    : ").append(accuracy.unexpectedWords()).append("\n");
        out.append("   substitutions       : ").append(accuracy.substitutions()).append("\n\n");

        out.append("3. FILLER WORD RECALL\n");
        out.append("   %.0f%% (%d of %d reference fillers kept)%n%n"
                .formatted(accuracy.fillerRecall() * 100, accuracy.recognisedFillers(), accuracy.referenceFillers()));

        out.append("4. REPETITION AND SELF-CORRECTION PRESERVATION\n");
        out.append("   %.0f%% (%d of %d immediate repetitions kept)%n%n"
                .formatted(
                        accuracy.repetitionRecall() * 100,
                        accuracy.recognisedRepetitions(),
                        accuracy.referenceRepetitions()));

        out.append("5. WORD TIMESTAMP AVAILABILITY\n");
        out.append("   available : ").append(timing.hasWordTimestamps()).append("\n");
        out.append("   model     : ").append(timing.model())
                .append(" (").append(timing.responseFormat()).append(")\n");
        out.append("   timed words: ").append(timing.words().size())
                .append(", segments: ").append(timing.segments().size()).append("\n");
        if (!timing.available()) {
            out.append("   reason    : ").append(timing.unavailableReason()).append("\n");
        }
        out.append("\n");

        out.append("6. AVERAGE WORD CONFIDENCE\n");
        out.append("   unavailable. ").append(transcription.model())
                .append(" does not return transcription logprobs, and whisper's avg_logprob is\n")
                .append("   uncalibrated, so no confidence figure is reported rather than an invented one.\n\n");

        out.append("7. NORMALIZED WORD ERROR RATE\n");
        out.append("   %.2f%%  (%d substitutions, %d deletions, %d insertions over %d reference words)%n%n"
                .formatted(
                        accuracy.wordErrorRate() * 100,
                        accuracy.substitutions(),
                        accuracy.deletions(),
                        accuracy.insertions(),
                        accuracy.referenceWords()));

        out.append("8. AUDIO FORMAT AND LATENCY\n");
        out.append("   transcript model    : ").append(transcription.model())
                .append(" (").append(transcription.responseFormat()).append(")\n");
        out.append("   timing model        : ").append(timing.model())
                .append(" (").append(timing.responseFormat()).append(")\n");
        out.append("   verbatim requested  : ").append(transcription.verbatimRequested()).append("\n");
        out.append("   transcription       : ").append(transcription.latencyMillis()).append(" ms\n");
        out.append("   timing              : ").append(timing.latencyMillis()).append(" ms\n");
        out.append("   scoring             : ").append(scoringMillis).append(" ms\n");
        out.append("   transcription tokens: in=").append(transcription.inputTokens())
                .append(" out=").append(transcription.outputTokens()).append("\n\n");

        out.append("DELIVERY METRICS SENT TO THE SCORER\n").append(metrics.summary()).append("\n");

        out.append("EVALUATION RETURNED\n");
        out.append("   estimated level : ").append(assessment.estimatedLevel()).append("/12\n");
        out.append("   confidence      : ").append(assessment.confidence()).append("\n");
        assessment.dimensions().forEach(dimension -> out.append("   ")
                .append(dimension.dimension().label())
                .append(": ")
                .append(dimension.score())
                .append(" - ")
                .append(dimension.evidence())
                .append("\n"));
        out.append("   source          : ").append(assessment.sourceRef()).append("\n");
        out.append("=".repeat(72)).append("\n");

        System.out.println(out);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new AssertionError(name + " must be set for the live test");
        }
        return value;
    }
}
