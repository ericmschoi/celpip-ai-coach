package com.listenspeak.coach.listening.generation;

import com.listenspeak.coach.platform.config.AppProperties;
import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.platform.web.ErrorCode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Picks the generator for the current content mode.
 *
 * <p>A {@code LIVE} deployment silently falling back to fixture content would
 * misrepresent what the user is practising, so a misconfigured {@code LIVE}
 * setup fails loudly instead.
 */
@Component
public class ExerciseGeneratorSelector {

    private static final Logger log = LoggerFactory.getLogger(ExerciseGeneratorSelector.class);

    private final AppProperties properties;
    private final List<ExerciseGenerator> generators;

    public ExerciseGeneratorSelector(AppProperties properties, List<ExerciseGenerator> generators) {
        this.properties = properties;
        this.generators = List.copyOf(generators);
        log.info("Listening content mode is {} with {} generator(s) registered", properties.contentMode(), generators.size());
    }

    /** Resolved per call, so which generator is available is never cached at startup. */
    public ExerciseGenerator forCurrentMode() {
        return generators.stream()
                .filter(generator -> generator.mode() == properties.contentMode())
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.PROVIDER_NOT_CONFIGURED,
                        "Live AI generation is selected but not configured. Set OPENAI_API_KEY, "
                                + "or run with APP_CONTENT_MODE=SEED to use the sample exercises."));
    }
}
