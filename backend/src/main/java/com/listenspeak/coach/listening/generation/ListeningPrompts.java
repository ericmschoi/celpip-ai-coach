package com.listenspeak.coach.listening.generation;

import com.listenspeak.coach.listening.Difficulty;
import com.listenspeak.coach.listening.domain.Part;
import com.listenspeak.coach.listening.domain.Skill;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Versioned prompts and the strict JSON schema for listening generation.
 *
 * <p>Kept in one class so a prompt change is a reviewable diff and so
 * {@code docs/prompts.md} has a single source to mirror. Bump
 * {@link #VERSION} whenever the wording changes.
 */
public final class ListeningPrompts {

    public static final String VERSION = "listening-generation-v1";
    public static final String SCHEMA_NAME = "listening_exercise";

    private ListeningPrompts() {}

    public static String systemPrompt() {
        return """
                You write original listening-comprehension practice material for adult English learners \
                preparing for a Canadian English proficiency test.

                Hard rules:
                - Everything you write must be original. Never reproduce, paraphrase, or imitate any real \
                  test question, recording, or published study material.
                - Never describe the material as official, certified, accredited, or as a real test.
                - Write dialogue that sounds like people actually talking: contractions, interruptions, \
                  someone changing their mind, ordinary Canadian settings and institutions.
                - Speaker turn text is spoken aloud verbatim by a voice model. Never include a speaker \
                  name, label, colon prefix, stage direction, or sound effect in the text field.
                - Distractors must be plausible to someone who half-listened, and unambiguously wrong to \
                  someone who listened properly. Never write two options that could both be defended.
                - Every question's evidence field must quote or closely paraphrase words that actually \
                  appear in your own speakerTurns.
                - Use only neutral, respectful content. No real names of living people, no politics, \
                  no medical or legal advice.
                """;
    }

    public static String userPrompt(Part part, Difficulty difficulty) {
        return """
                Write one complete listening exercise.

                Part %d - %s
                %s

                Speakers: exactly %d, each with at least two turns.
                Turns: between %d and %d.
                Transcript length: 250-600 words in total.

                Target difficulty: %s
                %s

                Write exactly 6 questions, each with exactly 4 options labelled A, B, C, D.
                Across the set, cover at least four different skills from: %s.
                Do not make all six questions literal detail questions.
                At least one question must require combining two separate statements.
                At least one question must ask what a speaker ends up deciding or how they feel about it.

                For every question also write:
                - explanation: why the answer is right and, briefly, why the nearest distractor is wrong.
                - evidence: the short phrase from the dialogue that settles it.

                Finally, write listeningTip: one concrete listening strategy someone could apply to the \
                next exercise of this type. Not generic encouragement.
                """
                .formatted(
                        part.number(),
                        part.label(),
                        part.profile(),
                        part.speakerCount(),
                        part.minTurns(),
                        part.maxTurns(),
                        difficulty.name(),
                        difficultyGuidance(difficulty),
                        skillNames());
    }

    /** Feedback loop for the single permitted regeneration attempt. */
    public static String retryPrompt(List<String> validationErrors) {
        return """
                Your previous exercise was rejected by automated validation. Write a new, complete \
                exercise that fixes every problem below. Do not explain the problems; just produce a \
                corrected exercise.

                %s
                """
                .formatted(validationErrors.stream().map(error -> "- " + error).collect(Collectors.joining("\n")));
    }

    private static String difficultyGuidance(Difficulty difficulty) {
        return switch (difficulty) {
            case DEVELOPING ->
                """
                Everyday vocabulary, short sentences, one idea per turn. The answer to most questions \
                is stated fairly directly, though not always in the same words as the option.\
                """;
            case COMPETENT ->
                """
                Natural pace and some idiom. Speakers qualify statements and occasionally talk past \
                each other. Several answers require paraphrase or a short inference rather than \
                recognition.\
                """;
            case ADVANCED ->
                """
                Dense, fast, idiomatic speech with implication, understatement, and hedging. Speakers \
                partly concede and revise positions. Most answers require inference, attitude reading, \
                or tracking a shift in position rather than recall.\
                """;
        };
    }

    private static String skillNames() {
        return Arrays.stream(Skill.values()).map(Enum::name).collect(Collectors.joining(", "));
    }

    /**
     * Strict JSON Schema. Structured Outputs requires every property to be listed
     * in {@code required} and {@code additionalProperties: false} on every object.
     */
    public static String jsonSchema() {
        return """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["title", "scenario", "speakerTurns", "questions", "listeningTip"],
                  "properties": {
                    "title": { "type": "string" },
                    "scenario": { "type": "string" },
                    "listeningTip": { "type": "string" },
                    "speakerTurns": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["speakerId", "speakerDisplayName", "text", "pauseAfterMs"],
                        "properties": {
                          "speakerId": { "type": "string" },
                          "speakerDisplayName": { "type": "string" },
                          "text": { "type": "string" },
                          "pauseAfterMs": { "type": "integer" }
                        }
                      }
                    },
                    "questions": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["id", "stem", "options", "correctOptionId", "explanation", "evidence", "skill"],
                        "properties": {
                          "id": { "type": "string" },
                          "stem": { "type": "string" },
                          "correctOptionId": { "type": "string", "enum": ["A", "B", "C", "D"] },
                          "explanation": { "type": "string" },
                          "evidence": { "type": "string" },
                          "skill": { "type": "string", "enum": [%s] },
                          "options": {
                            "type": "array",
                            "items": {
                              "type": "object",
                              "additionalProperties": false,
                              "required": ["id", "text"],
                              "properties": {
                                "id": { "type": "string", "enum": ["A", "B", "C", "D"] },
                                "text": { "type": "string" }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """
                .formatted(Arrays.stream(Skill.values())
                        .map(skill -> "\"" + skill.name() + "\"")
                        .collect(Collectors.joining(", ")));
    }
}
