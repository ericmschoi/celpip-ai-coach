package com.listenspeak.coach.listening.generation;

import com.listenspeak.coach.listening.Difficulty;
import com.listenspeak.coach.listening.audio.AudioStorage;
import com.listenspeak.coach.listening.domain.Part;
import com.listenspeak.coach.listening.seed.SeedExerciseDocument;
import com.listenspeak.coach.listening.seed.SeedExerciseLibrary;
import com.listenspeak.coach.platform.config.AppProperties.ContentMode;
import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.platform.web.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Serves the committed fixtures. This is what makes a fresh clone, CI, and the
 * whole Playwright suite work with no API key and no spend.
 *
 * <p>Fixture audio is uploaded to the audio store under a stable key the first
 * time it is needed, so seed and live exercises are served through exactly the
 * same presigned-URL path.
 */
@Component
public class SeedExerciseGenerator implements ExerciseGenerator {

    private final SeedExerciseLibrary library;
    private final AudioStorage audioStorage;

    public SeedExerciseGenerator(SeedExerciseLibrary library, AudioStorage audioStorage) {
        this.library = library;
        this.audioStorage = audioStorage;
    }

    @Override
    public ContentMode mode() {
        return ContentMode.SEED;
    }

    @Override
    public Generated generate(Part part, Difficulty difficulty) {
        SeedExerciseDocument document = library.find(part, difficulty)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.PROVIDER_NOT_CONFIGURED,
                        "Demo mode has no sample exercise for part %d yet. Set APP_CONTENT_MODE=LIVE "
                                        .formatted(part.number())
                                + "with an OpenAI key to generate a new one."));

        String audioKey = "listening/seed/%s.mp3".formatted(document.seedId());
        audioStorage.storeIfAbsent(audioKey, readAudio(document), "audio/mpeg");

        return new Generated(
                document.toGeneratedExercise(),
                audioKey,
                document.audioDurationSeconds(),
                "seed:" + document.seedId(),
                document.listeningTip());
    }

    private byte[] readAudio(SeedExerciseDocument document) {
        ClassPathResource resource = new ClassPathResource(document.audioResource());
        try (InputStream stream = resource.getInputStream()) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Missing seed audio " + document.audioResource(), e);
        }
    }
}
