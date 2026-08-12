package com.listenspeak.coach.listening.audio;

import com.listenspeak.coach.listening.domain.GeneratedExercise;
import com.listenspeak.coach.listening.domain.SpeakerTurn;
import com.listenspeak.coach.platform.media.MediaProcessingException;
import com.listenspeak.coach.platform.openai.OpenAiErrors;
import com.listenspeak.coach.platform.web.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import com.listenspeak.coach.platform.openai.OpenAiConfiguredCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Turns a dialogue into one recording.
 *
 * <p>Turns are synthesized concurrently, with a bounded number of provider
 * calls in flight, but they are always joined in dialogue order: the result
 * index comes from the turn's position, never from completion order.
 */
@Component
@Conditional(OpenAiConfiguredCondition.class)
public class DialogueRenderer {

    private static final Logger log = LoggerFactory.getLogger(DialogueRenderer.class);

    /** Bounded so a single exercise cannot exhaust the provider rate limit. */
    private static final int MAX_IN_FLIGHT = 4;

    private final TextToSpeech textToSpeech;
    private final AudioAssembler assembler;

    public DialogueRenderer(TextToSpeech textToSpeech, AudioAssembler assembler) {
        this.textToSpeech = textToSpeech;
        this.assembler = assembler;
    }

    public AudioAssembler.Assembled render(GeneratedExercise exercise) {
        List<SpeakerTurn> turns = exercise.speakerTurns();
        Map<String, String> voices = VoiceAssignment.assign(exercise.speakerIds());

        log.info("Rendering {} turns with voices {}", turns.size(), voices);

        Semaphore permits = new Semaphore(MAX_IN_FLIGHT);
        List<AudioAssembler.Segment> segments = new ArrayList<>(turns.size());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<byte[]>> rendered = turns.stream()
                    .map(turn -> executor.<byte[]>submit(() -> {
                        permits.acquire();
                        try {
                            // The spoken text is exactly the turn text: no label,
                            // no name, no stage direction.
                            return textToSpeech.synthesize(turn.text(), voices.get(turn.speakerId()));
                        } finally {
                            permits.release();
                        }
                    }))
                    .toList();

            for (int i = 0; i < turns.size(); i++) {
                segments.add(new AudioAssembler.Segment(
                        await(rendered.get(i)), turns.get(i).pauseAfterMs()));
            }
        }

        return assembler.assemble(segments);
    }

    private static byte[] await(Future<byte[]> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaProcessingException("Interrupted while synthesizing speech", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ApiException apiException) {
                throw apiException;
            }
            if (cause instanceof MediaProcessingException mediaException) {
                throw mediaException;
            }
            throw OpenAiErrors.translate("text-to-speech", cause);
        }
    }
}
