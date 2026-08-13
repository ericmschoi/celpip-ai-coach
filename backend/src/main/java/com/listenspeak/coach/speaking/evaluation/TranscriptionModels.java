package com.listenspeak.coach.speaking.evaluation;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * What each transcription model actually accepts.
 *
 * <p>This exists so the app never sends a parameter a model does not support and
 * then leans on a retry to recover. Sending {@code timestamp_granularities} to
 * {@code gpt-transcribe} and catching the resulting error worked, but it made a
 * failed request part of the normal path and hid a real capability gap behind a
 * fallback.
 *
 * <p>Sources, checked against the current API guide:
 *
 * <ul>
 *   <li>{@code timestamp_granularities} and {@code verbose_json} are supported
 *       <strong>only by whisper-1</strong>.
 *   <li>{@code gpt-transcribe} takes {@code prompt}, {@code keywords}, and
 *       {@code languages} (plural). It does not return transcription logprobs.
 *   <li>{@code whisper-1} takes {@code prompt} and {@code language} (singular).
 *   <li>Transcription logprobs are documented for the {@code gpt-4o-transcribe}
 *       variants only.
 * </ul>
 *
 * <p>An unrecognised model gets the conservative minimum, so a future model id
 * degrades to a working request rather than a rejected one.
 */
public final class TranscriptionModels {

    public enum Capability {
        /** Free-text style hint. */
        PROMPT,
        /** Literal terms expected in the audio; `keywords`. */
        KEYWORDS,
        /** Expected input languages; `languages`, plural. */
        LANGUAGES,
        /** Single expected language; `language`, singular. */
        LANGUAGE_SINGULAR,
        /** `response_format=verbose_json`. */
        VERBOSE_JSON,
        /** `timestamp_granularities[]`. */
        TIMESTAMP_GRANULARITIES,
        /** Token logprobs returned on the transcription. */
        LOGPROBS
    }

    private TranscriptionModels() {}

    public static Set<Capability> capabilitiesOf(String model) {
        String id = model == null ? "" : model.toLowerCase(Locale.ROOT);

        if (id.startsWith("whisper-1")) {
            return EnumSet.of(
                    Capability.PROMPT,
                    Capability.LANGUAGE_SINGULAR,
                    Capability.VERBOSE_JSON,
                    Capability.TIMESTAMP_GRANULARITIES);
        }
        if (id.startsWith("gpt-4o-transcribe") || id.startsWith("gpt-4o-mini-transcribe")) {
            return EnumSet.of(Capability.PROMPT, Capability.LANGUAGE_SINGULAR, Capability.LOGPROBS);
        }
        if (id.startsWith("gpt-transcribe")) {
            return EnumSet.of(Capability.PROMPT, Capability.KEYWORDS, Capability.LANGUAGES);
        }
        // Unknown model: send only what every transcription model accepts.
        return EnumSet.of(Capability.PROMPT);
    }

    public static boolean supports(String model, Capability capability) {
        return capabilitiesOf(model).contains(capability);
    }
}
