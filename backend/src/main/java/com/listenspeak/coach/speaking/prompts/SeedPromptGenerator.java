package com.listenspeak.coach.speaking.prompts;

import com.listenspeak.coach.platform.config.AppProperties.ContentMode;
import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.platform.web.ErrorCode;
import com.listenspeak.coach.speaking.SpeakingTaskCatalog.SpeakingTask;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * One committed original prompt per task, so the whole Speaking flow — timers,
 * recording, evaluation — is exercisable with no API key and no spend.
 */
@Component
public class SeedPromptGenerator implements PromptGenerator {

    private static final String RESOURCE = "seed/speaking/prompts.json";

    private record PromptDocument(int taskNumber, String situation, String instruction, List<String> bullets) {}

    private record PromptFile(List<PromptDocument> prompts) {}

    private final Map<Integer, PromptDocument> byTask;

    public SeedPromptGenerator(ObjectMapper objectMapper) {
        try (InputStream stream = new ClassPathResource(RESOURCE).getInputStream()) {
            PromptFile file = objectMapper.readValue(stream, PromptFile.class);
            this.byTask = file.prompts().stream()
                    .collect(Collectors.toUnmodifiableMap(PromptDocument::taskNumber, Function.identity()));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }

        if (byTask.size() != 8) {
            throw new IllegalStateException("Expected a seed prompt for all 8 tasks, found " + byTask.size());
        }
    }

    @Override
    public ContentMode mode() {
        return ContentMode.SEED;
    }

    @Override
    public Draft generate(SpeakingTask task) {
        PromptDocument document = byTask.get(task.taskNumber());
        if (document == null) {
            throw new ApiException(
                    ErrorCode.PROVIDER_NOT_CONFIGURED, "Demo mode has no prompt for task " + task.taskNumber() + ".");
        }
        return new Draft(
                document.situation(),
                document.instruction(),
                document.bullets(),
                "seed:speaking-task-" + task.taskNumber());
    }
}
