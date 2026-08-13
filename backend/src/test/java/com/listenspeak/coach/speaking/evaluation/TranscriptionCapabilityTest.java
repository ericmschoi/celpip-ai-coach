package com.listenspeak.coach.speaking.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.listenspeak.coach.speaking.evaluation.TranscriptionModels.Capability;
import org.junit.jupiter.api.Test;

/**
 * Guards the model-capability mapping.
 *
 * <p>A previous version sent {@code timestamp_granularities} to
 * {@code gpt-transcribe} and recovered through a fallback, which turned a
 * rejected request into part of the normal path and hid the capability gap.
 * These tests pin down what each model may be sent.
 */
class TranscriptionCapabilityTest {

    @Test
    void onlyWhisperSupportsTimestampGranularities() {
        assertThat(TranscriptionModels.supports("whisper-1", Capability.TIMESTAMP_GRANULARITIES))
                .isTrue();
        assertThat(TranscriptionModels.supports("gpt-transcribe", Capability.TIMESTAMP_GRANULARITIES))
                .isFalse();
        assertThat(TranscriptionModels.supports("gpt-4o-transcribe", Capability.TIMESTAMP_GRANULARITIES))
                .isFalse();
    }

    @Test
    void onlyWhisperSupportsVerboseJson() {
        assertThat(TranscriptionModels.supports("whisper-1", Capability.VERBOSE_JSON)).isTrue();
        assertThat(TranscriptionModels.supports("gpt-transcribe", Capability.VERBOSE_JSON))
                .isFalse();
    }

    @Test
    void gptTranscribeDoesNotReturnLogprobs() {
        assertThat(TranscriptionModels.supports("gpt-transcribe", Capability.LOGPROBS)).isFalse();
    }

    @Test
    void logprobsAreDocumentedOnlyForTheGpt4oTranscribeVariants() {
        assertThat(TranscriptionModels.supports("gpt-4o-transcribe", Capability.LOGPROBS)).isTrue();
        assertThat(TranscriptionModels.supports("gpt-4o-mini-transcribe", Capability.LOGPROBS))
                .isTrue();
        assertThat(TranscriptionModels.supports("whisper-1", Capability.LOGPROBS)).isFalse();
    }

    @Test
    void gptTranscribeTakesKeywordsAndPluralLanguages() {
        assertThat(TranscriptionModels.supports("gpt-transcribe", Capability.KEYWORDS)).isTrue();
        assertThat(TranscriptionModels.supports("gpt-transcribe", Capability.LANGUAGES)).isTrue();
        assertThat(TranscriptionModels.supports("gpt-transcribe", Capability.LANGUAGE_SINGULAR))
                .isFalse();
    }

    @Test
    void whisperTakesTheSingularLanguageParameterInstead() {
        assertThat(TranscriptionModels.supports("whisper-1", Capability.LANGUAGE_SINGULAR))
                .isTrue();
        assertThat(TranscriptionModels.supports("whisper-1", Capability.KEYWORDS)).isFalse();
        assertThat(TranscriptionModels.supports("whisper-1", Capability.LANGUAGES)).isFalse();
    }

    @Test
    void anUnknownModelGetsOnlyTheUniversallySupportedParameter() {
        var capabilities = TranscriptionModels.capabilitiesOf("some-future-model");

        assertThat(capabilities).containsExactly(Capability.PROMPT);
    }

    @Test
    void everyModelAcceptsAPrompt() {
        for (String model : new String[] {"gpt-transcribe", "whisper-1", "gpt-4o-transcribe"}) {
            assertThat(TranscriptionModels.supports(model, Capability.PROMPT))
                    .as(model)
                    .isTrue();
        }
    }
}
