package com.listenspeak.coach.listening.seed;

import com.listenspeak.coach.listening.Difficulty;
import com.listenspeak.coach.listening.domain.ExerciseValidator;
import com.listenspeak.coach.listening.domain.GeneratedExercise;
import com.listenspeak.coach.listening.domain.Part;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads the deterministic listening fixtures from the classpath at startup and
 * validates them with the same validator that guards live generation. A broken
 * fixture therefore fails the build's context load, not a user's practice
 * session.
 */
@Component
public class SeedExerciseLibrary {

    private static final Logger log = LoggerFactory.getLogger(SeedExerciseLibrary.class);
    private static final String LOCATION = "classpath:seed/listening/*.json";

    private final Map<String, SeedExerciseDocument> bySeedId = new LinkedHashMap<>();

    public SeedExerciseLibrary(ObjectMapper objectMapper, ExerciseValidator validator) {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not scan seed fixtures at " + LOCATION, e);
        }

        for (Resource resource : resources) {
            SeedExerciseDocument document = read(objectMapper, resource);
            GeneratedExercise exercise = document.toGeneratedExercise();

            ExerciseValidator.Result result = validator.validate(exercise);
            if (!result.isValid()) {
                throw new IllegalStateException(
                        "Seed fixture %s is invalid: %s".formatted(document.seedId(), result.summary()));
            }
            bySeedId.put(document.seedId(), document);
        }

        log.info("Loaded {} seed listening fixture(s): {}", bySeedId.size(), bySeedId.keySet());
    }

    private SeedExerciseDocument read(ObjectMapper objectMapper, Resource resource) {
        try (InputStream stream = resource.getInputStream()) {
            return objectMapper.readValue(stream, SeedExerciseDocument.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read seed fixture " + resource.getFilename(), e);
        }
    }

    public List<SeedExerciseDocument> all() {
        return List.copyOf(bySeedId.values());
    }

    public Optional<SeedExerciseDocument> byId(String seedId) {
        return Optional.ofNullable(bySeedId.get(seedId));
    }

    /**
     * Best fixture for a request: an exact part-and-difficulty match if one
     * exists, otherwise any fixture for that part. Difficulty is a style guide,
     * so serving a near match beats failing the practice session.
     */
    public Optional<SeedExerciseDocument> find(Part part, Difficulty difficulty) {
        List<SeedExerciseDocument> forPart = bySeedId.values().stream()
                .filter(document -> document.part() == part.number())
                .toList();

        return forPart.stream()
                .filter(document -> document.difficulty() == difficulty)
                .findFirst()
                .or(() -> forPart.stream().findFirst());
    }

    public boolean isEmpty() {
        return bySeedId.isEmpty();
    }
}
