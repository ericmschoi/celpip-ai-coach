package com.listenspeak.coach.speaking.prompts;

import com.listenspeak.coach.platform.config.AppProperties.ContentMode;
import com.listenspeak.coach.speaking.SpeakingTaskCatalog.SpeakingTask;
import java.util.List;

/** Produces an original prompt for one speaking task. */
public interface PromptGenerator {

    /**
     * @param sourceRef seed id or model name, recorded on the prompt
     */
    record Draft(String situation, String instruction, List<String> bullets, String sourceRef) {}

    ContentMode mode();

    Draft generate(SpeakingTask task);
}
