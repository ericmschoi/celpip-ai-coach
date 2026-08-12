package com.listenspeak.coach.speaking.evaluation;

import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Demo-mode transcriber, for when no provider is configured.
 *
 * <p>It cannot hear anything, so it returns <strong>nothing</strong>. It must
 * never return sample text: a previous version returned a fixed answer, which
 * the results screen then displayed under "What we heard" even when the user
 * had said nothing at all. Words the user did not say must never be attributed
 * to them.
 *
 * <p>Everything else in the flow — recording, timers, upload validation, FFmpeg
 * measurement, the results screen — is still exercised; only the parts that
 * genuinely require hearing the audio are reported as unavailable.
 */
@Component
@ConditionalOnMissingBean(OpenAiTranscriber.class)
public class SeedTranscriber implements Transcriber {

    @Override
    public String transcribe(Path recording, String filename) {
        return "";
    }

    @Override
    public boolean producesRealTranscript() {
        return false;
    }
}
