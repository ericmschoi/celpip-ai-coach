package com.listenspeak.coach.speaking.prompts;

import com.listenspeak.coach.listening.generation.StructuredResponses;
import com.listenspeak.coach.platform.config.AppProperties;
import com.listenspeak.coach.platform.config.AppProperties.ContentMode;
import com.listenspeak.coach.platform.openai.OpenAiConfiguredCondition;
import com.listenspeak.coach.platform.openai.OpenAiErrors;
import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.speaking.SpeakingTaskCatalog.SpeakingTask;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** Live prompt generation. Timings are never asked for: they come from the catalog. */
@Component
@Conditional(OpenAiConfiguredCondition.class)
public class OpenAiPromptGenerator implements PromptGenerator {

    private static final long MAX_OUTPUT_TOKENS = 800;

    private record PromptDocument(String situation, String instruction, List<String> bullets) {}

    private final OpenAIClient client;
    private final StructuredResponses structuredResponses;
    private final AppProperties properties;

    public OpenAiPromptGenerator(
            OpenAIClient client, StructuredResponses structuredResponses, AppProperties properties) {
        this.client = client;
        this.structuredResponses = structuredResponses;
        this.properties = properties;
    }

    @Override
    public ContentMode mode() {
        return ContentMode.LIVE;
    }

    @Override
    public Draft generate(SpeakingTask task) {
        var request = new StructuredResponses.Request(
                properties.openai().generationModel(),
                SpeakingPrompts.systemPrompt(),
                SpeakingPrompts.userPrompt(task),
                SpeakingPrompts.SCHEMA_NAME,
                SpeakingPrompts.jsonSchema(),
                MAX_OUTPUT_TOKENS);

        PromptDocument document;
        try {
            Response response = client.responses().create(structuredResponses.toParams(request));
            document = structuredResponses.parse(response, PromptDocument.class, "speaking prompt generation");
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw OpenAiErrors.translate("speaking prompt generation", e);
        }

        if (document.situation() == null
                || document.situation().isBlank()
                || document.instruction() == null
                || document.instruction().isBlank()) {
            throw new ApiException(
                    com.listenspeak.coach.platform.web.ErrorCode.GENERATION_INVALID,
                    "The generated prompt was incomplete. Try again.");
        }

        return new Draft(
                document.situation().trim(),
                document.instruction().trim(),
                document.bullets() == null ? List.of() : document.bullets(),
                "model:" + properties.openai().generationModel() + "/" + SpeakingPrompts.VERSION);
    }
}
