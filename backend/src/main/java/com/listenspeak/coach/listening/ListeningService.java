package com.listenspeak.coach.listening;

import com.listenspeak.coach.listening.api.CreateExerciseRequest;
import com.listenspeak.coach.listening.api.ExercisePublicView;
import com.listenspeak.coach.listening.api.SubmissionRequest;
import com.listenspeak.coach.listening.api.SubmissionResultView;
import com.listenspeak.coach.listening.audio.AudioStorage;
import com.listenspeak.coach.listening.domain.ListeningAttempt;
import com.listenspeak.coach.listening.domain.ListeningExercise;
import com.listenspeak.coach.listening.domain.Part;
import com.listenspeak.coach.listening.domain.Question;
import com.listenspeak.coach.listening.generation.ExerciseGenerator;
import com.listenspeak.coach.listening.generation.ExerciseGeneratorSelector;
import com.listenspeak.coach.platform.config.AppProperties;
import com.listenspeak.coach.platform.limits.LimitedAction;
import com.listenspeak.coach.platform.limits.UsageLimiter;
import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.platform.web.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Listening use cases. Ownership, answer secrecy, and scoring all live here so
 * a controller cannot get them wrong.
 */
@Service
public class ListeningService {

    private static final Logger log = LoggerFactory.getLogger(ListeningService.class);

    private final ExerciseGeneratorSelector generators;
    private final ListeningExerciseRepository repository;
    private final AudioStorage audioStorage;
    private final UsageLimiter usageLimiter;
    private final AppProperties properties;
    private final Clock clock;

    /**
     * Maps {@code ownerId|idempotencyKey} to the exercise it already created, so
     * a retried POST returns the original instead of paying to generate a second
     * one.
     *
     * <p>Known limitation: this is in-process, so it survives neither a restart
     * nor a second task. With one Fargate task and a short client retry window
     * that is enough; moving it to DynamoDB is the fix if the service ever
     * scales out. See docs/security.md.
     */
    private final Map<String, UUID> idempotentCreates = new ConcurrentHashMap<>();

    public ListeningService(
            ExerciseGeneratorSelector generators,
            ListeningExerciseRepository repository,
            AudioStorage audioStorage,
            UsageLimiter usageLimiter,
            AppProperties properties,
            Clock clock) {
        this.generators = generators;
        this.repository = repository;
        this.audioStorage = audioStorage;
        this.usageLimiter = usageLimiter;
        this.properties = properties;
        this.clock = clock;
    }

    public ExercisePublicView create(String ownerId, CreateExerciseRequest request, String idempotencyKey) {
        String idempotencyEntry = idempotencyEntry(ownerId, idempotencyKey);
        if (idempotencyEntry != null) {
            UUID existing = idempotentCreates.get(idempotencyEntry);
            if (existing != null) {
                return get(ownerId, existing);
            }
        }

        // Charged before generating, so a burst cannot get past the ceiling by
        // racing; a replayed idempotency key returns above without consuming.
        usageLimiter.consume(ownerId, LimitedAction.LISTENING_GENERATION);

        Part part = Part.ofNumber(request.part());
        ExerciseGenerator.Generated generated = generators.forCurrentMode().generate(part, request.difficulty());

        Instant now = clock.instant();
        ListeningExercise exercise = ListeningExercise.from(
                generated.exercise(),
                ownerId,
                generated.audioKey(),
                generated.durationSeconds(),
                generated.sourceRef(),
                generated.tip(),
                now,
                now.plus(properties.storage().exerciseTtl()));

        repository.save(exercise);

        if (idempotencyEntry != null) {
            idempotentCreates.put(idempotencyEntry, exercise.id());
        }

        log.info(
                "Created listening exercise id={} part={} difficulty={} source={} durationSeconds={}",
                exercise.id(),
                part.number(),
                request.difficulty(),
                generated.sourceRef(),
                generated.durationSeconds());

        return toPublicView(exercise);
    }

    public ExercisePublicView get(String ownerId, UUID exerciseId) {
        return toPublicView(requireOwned(ownerId, exerciseId));
    }

    public SubmissionResultView submit(String ownerId, UUID exerciseId, SubmissionRequest request) {
        ListeningExercise exercise = requireOwned(ownerId, exerciseId);

        if (repository.findAttemptByOwnerAndExercise(ownerId, exerciseId).isPresent()) {
            throw new ApiException(
                    ErrorCode.ALREADY_SUBMITTED,
                    "This exercise was already submitted. Start a new one to practise again.");
        }

        Map<String, String> selections = validateSelections(exercise, request);

        ListeningAttempt attempt = ListeningAttempt.score(exercise, ownerId, selections, clock.instant());
        repository.saveAttempt(attempt);

        log.info(
                "Scored listening attempt exercise={} correct={}/{} weakestSkill={}",
                exerciseId,
                attempt.correctCount(),
                attempt.totalQuestions(),
                attempt.weakestSkill());

        return SubmissionResultView.of(exercise, attempt, exercise.generalTip());
    }

    /**
     * Every question must be answered exactly once, with an option that exists
     * on that question. Anything else is a client bug, not a wrong answer.
     */
    private Map<String, String> validateSelections(ListeningExercise exercise, SubmissionRequest request) {
        Map<String, String> selections = new LinkedHashMap<>();
        for (SubmissionRequest.Answer answer : request.answers()) {
            if (selections.put(answer.questionId(), answer.selectedOptionId()) != null) {
                throw ApiException.validation(
                        "Question %s was answered more than once.".formatted(answer.questionId()));
            }
        }

        Set<String> expected = exercise.questions().stream().map(Question::id).collect(Collectors.toSet());
        if (!selections.keySet().equals(expected)) {
            throw ApiException.validation("Answer every question exactly once before submitting.");
        }

        for (Question question : exercise.questions()) {
            String selected = selections.get(question.id());
            boolean known =
                    question.options().stream().anyMatch(option -> option.id().equals(selected));
            if (!known) {
                throw ApiException.validation("Question %s has no option %s.".formatted(question.id(), selected));
            }
        }
        return selections;
    }

    private ListeningExercise requireOwned(String ownerId, UUID exerciseId) {
        return repository
                .findByOwnerAndId(ownerId, exerciseId)
                // A miss and a cross-user hit are indistinguishable on purpose.
                .orElseThrow(() -> ApiException.notFound("Exercise"));
    }

    private ExercisePublicView toPublicView(ListeningExercise exercise) {
        String url =
                audioStorage.presignedUrl(exercise.audioKey(), properties.storage().presignedUrlTtl());
        return ExercisePublicView.of(exercise, url);
    }

    private static String idempotencyEntry(String ownerId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return ownerId + "|" + idempotencyKey.trim();
    }
}
