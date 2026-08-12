package com.listenspeak.coach.listening.generation;

import com.listenspeak.coach.platform.openai.OpenAiErrors;
import com.openai.core.JsonValue;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseTextConfig;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * One place that speaks the Responses API with strict Structured Outputs.
 *
 * <p>The model is asked for JSON that conforms to a schema, and the result is
 * parsed as JSON - never scraped out of prose with regular expressions. A
 * content refusal is surfaced as its own error rather than being retried.
 */
@Component
public class StructuredResponses {

    private static final Logger log = LoggerFactory.getLogger(StructuredResponses.class);

    private final ObjectMapper objectMapper;

    public StructuredResponses(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record Request(
            String model,
            String systemPrompt,
            String userPrompt,
            String schemaName,
            String jsonSchema,
            Long maxOutputTokens) {}

    public ResponseCreateParams toParams(Request request) {
        ResponseFormatTextJsonSchemaConfig format = ResponseFormatTextJsonSchemaConfig.builder()
                .name(request.schemaName())
                .schema(schemaOf(request.jsonSchema()))
                // Strict mode is what makes the shape a guarantee rather than a hope.
                .strict(true)
                .build();

        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                .model(request.model())
                .instructions(request.systemPrompt())
                .input(request.userPrompt())
                .text(ResponseTextConfig.builder().format(format).build())
                // Nothing is retained provider-side; this app keeps its own copy.
                .store(false);

        if (request.maxOutputTokens() != null) {
            builder.maxOutputTokens(request.maxOutputTokens());
        }
        return builder.build();
    }

    /** Extracts the JSON payload, mapping refusals and empty output to stable errors. */
    public <T> T parse(Response response, Class<T> type, String operation) {
        String json = null;

        for (var item : response.output()) {
            var message = item.message();
            if (message.isEmpty()) {
                continue;
            }
            for (ResponseOutputMessage.Content content : message.get().content()) {
                if (content.isRefusal()) {
                    throw OpenAiErrors.refused(
                            "The AI declined to produce this content. Try a different part or difficulty.");
                }
                if (content.isOutputText()) {
                    json = content.asOutputText().text();
                }
            }
        }

        if (json == null || json.isBlank()) {
            throw OpenAiErrors.translate(operation, new IllegalStateException("no output text in response"));
        }

        response.usage()
                .ifPresent(usage -> log.info(
                        "OpenAI {} usage inputTokens={} outputTokens={} totalTokens={}",
                        operation,
                        usage.inputTokens(),
                        usage.outputTokens(),
                        usage.totalTokens()));

        try {
            return objectMapper.readValue(json, type);
        } catch (RuntimeException e) {
            // Schema-conformant JSON that still will not bind is a provider
            // problem, not a user problem.
            throw OpenAiErrors.translate(operation, e);
        }
    }

    @SuppressWarnings("unchecked")
    private ResponseFormatTextJsonSchemaConfig.Schema schemaOf(String jsonSchema) {
        Map<String, Object> parsed = objectMapper.readValue(jsonSchema, Map.class);

        ResponseFormatTextJsonSchemaConfig.Schema.Builder builder =
                ResponseFormatTextJsonSchemaConfig.Schema.builder();
        parsed.forEach((key, value) -> builder.putAdditionalProperty(key, JsonValue.from(value)));
        return builder.build();
    }

    /** Timeouts are per-request so a slow generation cannot hold a connection forever. */
    public static Duration defaultTimeout() {
        return Duration.ofSeconds(120);
    }
}
