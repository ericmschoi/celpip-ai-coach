package com.listenspeak.coach.listening.generation;

import com.listenspeak.coach.listening.Difficulty;
import com.listenspeak.coach.listening.audio.AudioAssembler;
import com.listenspeak.coach.listening.audio.DialogueRenderer;
import com.listenspeak.coach.listening.audio.AudioStorage;
import com.listenspeak.coach.listening.domain.ExerciseValidator;
import com.listenspeak.coach.listening.domain.GeneratedExercise;
import com.listenspeak.coach.listening.domain.Part;
import com.listenspeak.coach.platform.config.AppProperties;
import com.listenspeak.coach.platform.config.AppProperties.ContentMode;
import com.listenspeak.coach.platform.media.MediaProcessingException;
import com.listenspeak.coach.platform.openai.OpenAiConfiguredCondition;
import com.listenspeak.coach.platform.openai.OpenAiErrors;
import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.platform.web.ErrorCode;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * The live pipeline: generate the exercise, validate it, synthesize each turn
 * with a stable per-speaker voice, assemble the audio, and store it.
 *
 * <p>Generation is retried <strong>at most once</strong>, with the validation
 * errors fed back. After that the request fails with a stable error rather than
 * looping against a paid API.
 */
@Component
@Conditional(OpenAiConfiguredCondition.class)
public class OpenAiExerciseGenerator implements ExerciseGenerator {

    private static final Logger log = LoggerFactory.getLogger(OpenAiExerciseGenerator.class);

    /** Enough headroom for a 600-word transcript plus six explained questions. */
    private static final long MAX_OUTPUT_TOKENS = 8_000;

    private final OpenAIClient client;
    private final StructuredResponses structuredResponses;
    private final ExerciseValidator validator;
    private final DialogueRenderer dialogueRenderer;
    private final AudioStorage audioStorage;
    private final AppProperties properties;

    public OpenAiExerciseGenerator(
            OpenAIClient client,
            StructuredResponses structuredResponses,
            ExerciseValidator validator,
            DialogueRenderer dialogueRenderer,
            AudioStorage audioStorage,
            AppProperties properties) {
        this.client = client;
        this.structuredResponses = structuredResponses;
        this.validator = validator;
        this.dialogueRenderer = dialogueRenderer;
        this.audioStorage = audioStorage;
        this.properties = properties;
    }

    @Override
    public ContentMode mode() {
        return ContentMode.LIVE;
    }

    @Override
    public Generated generate(Part part, Difficulty difficulty) {
        GeneratedExerciseDocument document = generateValidated(part, difficulty);
        GeneratedExercise exercise = document.toDomain(part, difficulty);

        AudioAssembler.Assembled audio = renderAudio(exercise);

        String audioKey = "listening/generated/%s.mp3".formatted(UUID.randomUUID());
        audioStorage.store(audioKey, audio.mp3(), "audio/mpeg");

        return new Generated(
                exercise,
                audioKey,
                (int) Math.round(audio.durationSeconds()),
                "model:" + properties.openai().generationModel() + "/" + ListeningPrompts.VERSION,
                document.listeningTip());
    }

    /** Generates, validates, and allows exactly one corrective retry. */
    private GeneratedExerciseDocument generateValidated(Part part, Difficulty difficulty) {
        String userPrompt = ListeningPrompts.userPrompt(part, difficulty);

        for (int attempt = 1; attempt <= 2; attempt++) {
            GeneratedExerciseDocument document = requestExercise(userPrompt);
            ExerciseValidator.Result result;

            try {
                result = validator.validate(document.toDomain(part, difficulty));
            } catch (ApiException e) {
                // Could not even be mapped to the domain; treat as a validation failure.
                result = new ExerciseValidator.Result(List.of(e.getMessage()));
            }

            if (result.isValid()) {
                return document;
            }

            log.warn(
                    "Generated exercise failed validation on attempt {}/2 part={} errors={}",
                    attempt,
                    part.number(),
                    result.errors());

            if (attempt == 2) {
                throw new ApiException(
                        ErrorCode.GENERATION_INVALID,
                        "The generated exercise did not pass quality checks twice. Try again in a moment.");
            }
            userPrompt = ListeningPrompts.userPrompt(part, difficulty) + "\n\n"
                    + ListeningPrompts.retryPrompt(result.errors());
        }

        throw new IllegalStateException("unreachable");
    }

    private GeneratedExerciseDocument requestExercise(String userPrompt) {
        var request = new StructuredResponses.Request(
                properties.openai().generationModel(),
                ListeningPrompts.systemPrompt(),
                userPrompt,
                ListeningPrompts.SCHEMA_NAME,
                ListeningPrompts.jsonSchema(),
                MAX_OUTPUT_TOKENS);

        try {
            Response response = client.responses().create(structuredResponses.toParams(request));
            return structuredResponses.parse(response, GeneratedExerciseDocument.class, "exercise generation");
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw OpenAiErrors.translate("exercise generation", e);
        }
    }

    private AudioAssembler.Assembled renderAudio(GeneratedExercise exercise) {
        try {
            return dialogueRenderer.render(exercise);
        } catch (MediaProcessingException e) {
            log.error("Audio pipeline failed for {} turns", exercise.speakerTurns().size(), e);
            throw new ApiException(
                    ErrorCode.AUDIO_QUALITY_FAILED,
                    "The exercise audio did not pass quality checks. Generating a new exercise usually works.",
                    e);
        }
    }
}
