package com.listenspeak.coach.config;

import com.listenspeak.coach.listening.Difficulty;
import com.listenspeak.coach.platform.config.AppProperties;
import com.listenspeak.coach.listening.seed.SeedExerciseLibrary;
import com.listenspeak.coach.speaking.SpeakingTaskCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the frontend how this deployment is configured. Everything here is
 * non-sensitive by construction: no keys, no endpoints, no identifiers that are
 * not already public in the bundle.
 */
@RestController
@RequestMapping("/api/v1/config")
@Tag(name = "Config", description = "Client bootstrap configuration")
public class ConfigController {

    private final AppProperties properties;
    private final SeedExerciseLibrary seedLibrary;

    public ConfigController(AppProperties properties, SeedExerciseLibrary seedLibrary) {
        this.properties = properties;
        this.seedLibrary = seedLibrary;
    }

    public record SpeakingTaskView(
            int taskNumber, String title, String focus, int preparationSeconds, int answerSeconds) {}

    public record DailyLimitsView(int listening, int speaking) {}

    public record AppConfigView(
            AppProperties.ContentMode contentMode,
            AppProperties.AuthMode authMode,
            List<Integer> listeningParts,
            /** Parts that have an offline sample. Only these work in SEED mode. */
            List<Integer> seedListeningParts,
            List<SpeakingTaskView> speakingTasks,
            List<Difficulty> difficulties,
            DailyLimitsView dailyLimits) {}

    @GetMapping
    @Operation(summary = "Client bootstrap configuration")
    public AppConfigView get() {
        List<SpeakingTaskView> tasks = SpeakingTaskCatalog.all().stream()
                .map(task -> new SpeakingTaskView(
                        task.taskNumber(),
                        task.title(),
                        task.focus(),
                        (int) task.preparation().toSeconds(),
                        (int) task.answer().toSeconds()))
                .toList();

        List<Integer> seedParts = seedLibrary.all().stream()
                .map(document -> document.part())
                .distinct()
                .sorted()
                .toList();

        return new AppConfigView(
                properties.contentMode(),
                properties.auth().mode(),
                List.of(1, 2, 3, 4, 5, 6),
                seedParts,
                tasks,
                Arrays.asList(Difficulty.values()),
                new DailyLimitsView(
                        properties.limits().listeningPerDay(), properties.limits().speakingPerDay()));
    }
}
