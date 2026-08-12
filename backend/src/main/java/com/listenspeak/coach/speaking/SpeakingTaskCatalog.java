package com.listenspeak.coach.speaking;

import com.listenspeak.coach.platform.web.ApiException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The one place task timings live. Components and the recorder read these
 * values from {@code GET /api/v1/config} rather than hard-coding seconds.
 */
public final class SpeakingTaskCatalog {

    public record SpeakingTask(
            int taskNumber,
            String title,
            String focus,
            Duration preparation,
            Duration answer) {}

    private static final List<SpeakingTask> TASKS = List.of(
            new SpeakingTask(
                    1,
                    "Giving Advice",
                    "Advise one person about a specific decision, with reasons.",
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(90)),
            new SpeakingTask(
                    2,
                    "Talking about a Personal Experience",
                    "Narrate one past experience with clear sequence and detail.",
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(60)),
            new SpeakingTask(
                    3,
                    "Describing a Scene",
                    "Describe what is happening in a scene so a listener can picture it.",
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(60)),
            new SpeakingTask(
                    4,
                    "Making Predictions",
                    "Predict what happens next in a scene and justify the prediction.",
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(60)),
            new SpeakingTask(
                    5,
                    "Comparing and Persuading",
                    "Choose between two options and persuade a specific listener.",
                    Duration.ofSeconds(60),
                    Duration.ofSeconds(60)),
            new SpeakingTask(
                    6,
                    "Dealing with a Difficult Situation",
                    "Choose an audience, then manage an awkward situation tactfully.",
                    Duration.ofSeconds(60),
                    Duration.ofSeconds(60)),
            new SpeakingTask(
                    7,
                    "Expressing Opinions",
                    "State a position on an issue and support it with reasons.",
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(90)),
            new SpeakingTask(
                    8,
                    "Describing an Unusual Situation",
                    "Describe something unfamiliar precisely enough for someone who cannot see it.",
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(60)));

    private static final Map<Integer, SpeakingTask> BY_NUMBER =
            TASKS.stream().collect(Collectors.toUnmodifiableMap(SpeakingTask::taskNumber, Function.identity()));

    private SpeakingTaskCatalog() {}

    public static List<SpeakingTask> all() {
        return TASKS;
    }

    public static SpeakingTask require(int taskNumber) {
        SpeakingTask task = BY_NUMBER.get(taskNumber);
        if (task == null) {
            throw ApiException.validation("Speaking task must be between 1 and 8.");
        }
        return task;
    }
}
