package com.listenspeak.coach.platform.openai;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches only when an OpenAI key is actually present.
 *
 * <p>{@code @ConditionalOnProperty} is not enough: the property is always
 * declared and defaults to an empty string, which that annotation treats as
 * "present".
 */
public class OpenAiConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String apiKey = context.getEnvironment().getProperty("app.openai.api-key");
        return apiKey != null && !apiKey.isBlank();
    }
}
