package com.listenspeak.coach.speaking.evaluation;

import com.listenspeak.coach.speaking.SpeakingTaskCatalog.SpeakingTask;
import com.listenspeak.coach.speaking.domain.DeliveryMetrics;

/**
 * Versioned prompt and strict schema for speaking evaluation. Mirrored in
 * {@code docs/prompts.md}; bump {@link #VERSION} when the wording changes.
 */
public final class ScoringPrompts {

    public static final String VERSION = "speaking-scoring-v2";
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
                - The transcript is verbatim: fillers, repeated words, false starts, and \
                  self-corrections are the speaker's own and are evidence, not transcription noise. \
                  Do not treat them as errors in the transcript.
                - A transcript hides errors and shows others. Where the transcript is ambiguous, say \
                  so rather than guessing.
                - Judge language, not opinions. A well-argued view you disagree with scores well. An \
                  incoherent view you agree with does not.
                - Score conservatively. When the evidence is thin, give a lower confidence rather \
                  than a confident middling score.
                - Corrections must preserve what the speaker meant. Do not substitute your own ideas.
                - The sample answer must be something this speaker could plausibly produce next \
                  time: one clear step up, not a model essay.

                Score each dimension separately against its own descriptor. Do not let one strong \
                dimension pull the others up.

                CONTENT AND COHERENCE - is there enough, and does it hold together?
                - 1-4: fragments; no discernible line of thought; the listener reconstructs it.
                - 5-7: a position and some support, but thin, repetitive, or loosely ordered.
                - 8-10: a clear position, developed with reasons and specifics, in a followable order.
                - 11-12: tightly organised, each point earning its place, with a purposeful close.

                VOCABULARY - range and precision of word choice.
                - 1-4: very limited; the wrong word often blocks the meaning.
                - 5-7: everyday vocabulary that gets by; noticeable repetition of the same words.
                - 8-10: reasonable range with some precise or idiomatic choices; occasional awkwardness.
                - 11-12: precise, varied, and appropriate in register throughout.

                LISTENABILITY - how much work the listener has to do. Judge only from the metrics \
                supplied and from whether the wording itself is easy to follow. Never from sound.
                - 1-4: constant hesitation or very uneven pace; hard to follow.
                - 5-7: understandable but effortful; frequent fillers, restarts, or long pauses.
                - 8-10: generally smooth; hesitation does not interrupt the thread.
                - 11-12: fluent and well-paced, with pauses that mark structure rather than trouble.

                TASK FULFILLMENT - did they do what was asked, for the audience asked?
                - 1-4: largely off-task, or far too little to judge.
                - 5-7: addresses the task partly; misses a required element or the stated audience.
                - 8-10: does what was asked, addresses the right audience, uses the time.
                - 11-12: fully meets the task, including the harder parts, with nothing padded.

                The overall estimatedLevel is an unofficial 1-12 summary that must be explainable by \
                those four scores.
                """;
    }

    public static String userPrompt(
            SpeakingTask task, String promptText, String transcript, DeliveryMetrics metrics) {

        return """
                TASK %d - %s
                What this task requires: %s
                %s

                PROMPT GIVEN TO THE SPEAKER
                %s

                TIME LIMIT
                %d seconds

                TRANSCRIPT OF THE ANSWER (verbatim, including fillers and false starts)
                %s

                DELIVERY MEASUREMENTS (computed locally from the audio, not heard)
                %s

                Assess this answer on the four dimensions, each against its own descriptor. For each, \
                give a 1-12 score and evidence that quotes the transcript or cites a specific \
                measurement above.

                Then give: two genuine strengths, the two highest-priority improvements, specific \
                corrected phrases taken from what they said, one stronger sample answer that keeps \
                their intended meaning, and one concrete drill to do next.
                """
                .formatted(
                        task.taskNumber(),
                        task.title(),
                        task.focus(),
                        contextNote(task),
                        promptText,
                        (int) task.answer().toSeconds(),
                        transcript,
                        metrics.summary());
    }

    /**
     * Extra framing the scorer needs but cannot infer from the prompt text.
     *
     * <p>Tasks 3, 4, and 8 are visual in the real test: the speaker describes or
     * predicts from a picture. This app has no image, so the scene is given to
     * the speaker as prose. The scorer is told this explicitly, because
     * otherwise it may penalise a description for not matching a picture it was
     * never shown, or credit accuracy it cannot possibly check.
     *
     * <p>Task 6 lets the speaker choose whom they are addressing, so the scorer
     * is told to identify that choice from the transcript rather than assume one.
     */
    static String contextNote(SpeakingTask task) {
        return switch (task.taskNumber()) {
            case 3, 4, 8 ->
                """

                VISUAL CONTEXT
                This task is visual in the real test. This practice tool shows no image: the scene is \
                described to the speaker in words, in the prompt below. Judge the description against \
                that written scene only. You cannot verify visual accuracy, so do not claim the \
                speaker missed or invented a detail unless the written scene settles it.\
                """;
            case 6 ->
                """

                AUDIENCE
                This task requires the speaker to choose whom they are addressing and to speak to that \
                person directly. Identify from the transcript who they chose. If they never make the \
                audience clear, that is a Task Fulfillment failure; do not assume one.\
                """;
            case 5 ->
                """

                AUDIENCE
                This task names a specific listener to persuade. Judge whether the speaker actually \
                addressed that listener's concerns rather than arguing in general.\
                """;
            default -> "";
        };
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
