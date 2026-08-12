package com.listenspeak.coach.speaking.prompts;

import com.listenspeak.coach.speaking.SpeakingTaskCatalog.SpeakingTask;

/**
 * Versioned prompts and schema for speaking-task generation. Mirrored in
 * {@code docs/prompts.md}; bump {@link #VERSION} when the wording changes.
 */
public final class SpeakingPrompts {

    public static final String VERSION = "speaking-prompt-v1";
    public static final String SCHEMA_NAME = "speaking_prompt";

    private SpeakingPrompts() {}

    public static String systemPrompt() {
        return """
                You write original speaking-practice prompts for adult English learners preparing for a \
                Canadian English proficiency test.

                Hard rules:
                - Everything original. Never reproduce or imitate a real test prompt.
                - Never describe the prompt as official, certified, or part of a real test.
                - Use everyday Canadian settings: a community centre, a landlord, a co-worker, a \
                  neighbourhood shop, a transit delay.
                - The situation must be concrete and answerable by anyone, without special knowledge, \
                  a specific job, or a particular family situation.
                - Neutral and respectful. No politics, religion, medical or legal advice, no real \
                  living people, nothing distressing.
                - Do not state a time limit or say how long to speak. The application controls timing.
                """;
    }

    public static String userPrompt(SpeakingTask task) {
        return """
                Write one speaking prompt.

                Task %d - %s
                What this task is for: %s

                Produce:
                - situation: two or three sentences setting up a concrete scenario.
                - instruction: one sentence saying exactly what the speaker must do.
                - bullets: three short supporting points that guide a full answer without \
                  scripting it.

                Keep the whole prompt under 90 words.
                """
                .formatted(task.taskNumber(), task.title(), task.focus());
    }

    public static String jsonSchema() {
        return """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["situation", "instruction", "bullets"],
                  "properties": {
                    "situation": { "type": "string" },
                    "instruction": { "type": "string" },
                    "bullets": {
                      "type": "array",
                      "items": { "type": "string" }
                    }
                  }
                }
                """;
    }
}
