package com.listenspeak.coach.speaking.evaluation;

import com.listenspeak.coach.speaking.SpeakingTaskCatalog.SpeakingTask;
import com.listenspeak.coach.speaking.domain.DeliveryMetrics;

/**
 * Versioned prompt and strict schema for speaking evaluation. Mirrored in
 * {@code docs/prompts.md}; bump {@link #VERSION} when the wording changes.
 */
public final class ScoringPrompts {

    public static final String VERSION = "speaking-scoring-v1";
    public static final String SCHEMA_NAME = "speaking_evaluation";

    private ScoringPrompts() {}

    public static String systemPrompt() {
        return """
                You assess spoken English for a practice tool. You are given a task prompt, a \
                transcript of what the speaker actually said, the time limit, and coarse delivery \
                measurements. You never hear the audio.

                Hard rules:
                - Ground every judgement in the supplied transcript or the supplied metrics. Quote \
                  the speaker's own words in every piece of evidence. Never invent an example.
                - You cannot hear pronunciation, accent, intonation, or voice quality. Never comment \
                  on them, and never claim a word was mispronounced. Listenability must be judged \
                  from what the metrics actually show - pace, pausing, hesitation, repetition - and \
                  from whether the wording itself is easy to follow.
                - A transcript hides errors and shows others. Where the transcript is ambiguous, say \
                  so rather than guessing.
                - Judge language, not opinions. A well-argued view you disagree with scores well. An \
                  incoherent view you agree with does not.
                - Score conservatively. When the evidence is thin, give a lower confidence rather \
                  than a confident middling score.
                - Corrections must preserve what the speaker meant. Do not substitute your own ideas.
                - The sample answer must be something this speaker could plausibly produce next \
                  time: one clear step up, not a model essay.

                Level guide, 1 to 12, unofficial:
                - 1-4: fragmentary; the listener has to reconstruct the meaning.
                - 5-7: gets the message across; limited range, noticeable repetition, some parts \
                  need effort to follow.
                - 8-10: clear, organised, reasonably varied; occasional awkwardness that does not \
                  impede understanding.
                - 11-12: fluent, precise, well-structured, with control of register and nuance.
                """;
    }

    public static String userPrompt(
            SpeakingTask task, String promptText, String transcript, DeliveryMetrics metrics) {

        return """
                TASK %d - %s
                What this task requires: %s

                PROMPT GIVEN TO THE SPEAKER
                %s

                TIME LIMIT
                %d seconds

                TRANSCRIPT OF THE ANSWER
                %s

                DELIVERY MEASUREMENTS (computed locally, not heard)
                %s

                Assess this answer on the four dimensions. For each, give a 1-12 score and evidence \
                that quotes the transcript or cites a specific measurement above.

                Then give: two genuine strengths, the two highest-priority improvements, specific \
                corrected phrases taken from what they said, one stronger sample answer that keeps \
                their intended meaning, and one concrete drill to do next.
                """
                .formatted(
                        task.taskNumber(),
                        task.title(),
                        task.focus(),
                        promptText,
                        (int) task.answer().toSeconds(),
                        transcript,
                        metrics.summary());
    }

    public static String jsonSchema() {
        return """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["estimatedLevel", "confidence", "dimensions", "strengths",
                               "improvements", "corrections", "sampleAnswer", "nextDrill"],
                  "properties": {
                    "estimatedLevel": { "type": "integer", "minimum": 1, "maximum": 12 },
                    "confidence": { "type": "string", "enum": ["LOW", "MEDIUM", "HIGH"] },
                    "sampleAnswer": { "type": "string" },
                    "nextDrill": { "type": "string" },
                    "strengths": { "type": "array", "items": { "type": "string" } },
                    "dimensions": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["dimension", "score", "evidence"],
                        "properties": {
                          "dimension": {
                            "type": "string",
                            "enum": ["CONTENT_COHERENCE", "VOCABULARY", "LISTENABILITY", "TASK_FULFILLMENT"]
                          },
                          "score": { "type": "integer", "minimum": 1, "maximum": 12 },
                          "evidence": { "type": "string" }
                        }
                      }
                    },
                    "improvements": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["issue", "whyItMatters", "howToFix"],
                        "properties": {
                          "issue": { "type": "string" },
                          "whyItMatters": { "type": "string" },
                          "howToFix": { "type": "string" }
                        }
                      }
                    },
                    "corrections": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["original", "improved", "reason"],
                        "properties": {
                          "original": { "type": "string" },
                          "improved": { "type": "string" },
                          "reason": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
    }
}
