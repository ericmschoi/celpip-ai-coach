package com.listenspeak.coach.listening.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Application-level validation of generated exercises, applied on top of the
 * provider's JSON-schema enforcement. A schema can guarantee shape; it cannot
 * guarantee that the answer is supportable, that a distractor is not a
 * duplicate, or that the model did not describe its own output as official.
 *
 * <p>Failures are collected rather than thrown one at a time, because the whole
 * list is fed back into the single permitted regeneration attempt.
 */
@Component
public class ExerciseValidator {

    public static final int REQUIRED_QUESTIONS = 6;
    public static final int REQUIRED_OPTIONS = 4;

    /** Words a generated exercise must never use to describe itself. */
    private static final Pattern SELF_DESCRIBED_AS_OFFICIAL = Pattern.compile(
            "\\b(official|paragon testing|real celpip|actual celpip|celpip-general test|"
                    + "certified|accredited)\\b",
            Pattern.CASE_INSENSITIVE);

    /** A spoken line must not carry its own speaker label; TTS would read it out. */
    private static final Pattern SPEAKER_LABEL_PREFIX = Pattern.compile("^\\s*[A-Z][A-Za-z .'-]{1,24}\\s*:");

    public record Result(List<String> errors) {

        public boolean isValid() {
            return errors.isEmpty();
        }

        public String summary() {
            return String.join(" ", errors);
        }
    }

    public Result validate(GeneratedExercise exercise) {
        List<String> errors = new ArrayList<>();

        validateText(exercise, errors);
        validateSpeakers(exercise, errors);
        validateTranscriptSize(exercise, errors);
        validateQuestions(exercise, errors);

        return new Result(List.copyOf(errors));
    }

    private void validateText(GeneratedExercise exercise, List<String> errors) {
        if (isBlank(exercise.title())) {
            errors.add("title must not be empty.");
        }
        if (isBlank(exercise.scenario())) {
            errors.add("scenario must not be empty.");
        }
        String combined = (exercise.title() + " " + exercise.scenario() + " " + exercise.transcriptText());
        if (SELF_DESCRIBED_AS_OFFICIAL.matcher(combined).find()) {
            errors.add("Content must not describe itself as official, certified, or as a real CELPIP test.");
        }
    }

    private void validateSpeakers(GeneratedExercise exercise, List<String> errors) {
        if (exercise.speakerTurns().isEmpty()) {
            errors.add("speakerTurns must not be empty.");
            return;
        }

        int expected = exercise.part().speakerCount();
        int actual = exercise.speakerIds().size();
        if (actual != expected) {
            errors.add("Part %d requires exactly %d distinct speakers but %d were used."
                    .formatted(exercise.part().number(), expected, actual));
        }

        for (SpeakerTurn turn : exercise.speakerTurns()) {
            if (SPEAKER_LABEL_PREFIX.matcher(turn.text()).find()) {
                errors.add("Turn text must not begin with a speaker label such as \"%s:\"."
                        .formatted(turn.speakerDisplayName()));
                break;
            }
        }

        // The same speakerId must always map to the same display name, or the
        // transcript and the voice assignment disagree.
        long distinctPairs = exercise.speakerTurns().stream()
                .map(turn -> turn.speakerId() + "|" + turn.speakerDisplayName())
                .distinct()
                .count();
        if (distinctPairs != exercise.speakerIds().size()) {
            errors.add("Each speakerId must always use the same display name.");
        }

        // Every speaker must actually take part, otherwise a "who said it"
        // question has no basis in the audio.
        for (String speakerId : exercise.speakerIds()) {
            long turns = exercise.speakerTurns().stream()
                    .filter(turn -> turn.speakerId().equals(speakerId))
                    .count();
            if (turns < 2) {
                errors.add("Speaker %s has fewer than two turns.".formatted(speakerId));
            }
        }
    }

    private void validateTranscriptSize(GeneratedExercise exercise, List<String> errors) {
        Part part = exercise.part();
        int turns = exercise.speakerTurns().size();
        if (turns < part.minTurns() || turns > part.maxTurns()) {
            errors.add("Part %d expects between %d and %d turns but has %d."
                    .formatted(part.number(), part.minTurns(), part.maxTurns(), turns));
        }

        int words = exercise.transcriptWordCount();
        if (words < 150) {
            errors.add("Transcript is too short to support six questions (%d words).".formatted(words));
        }
        if (words > 900) {
            errors.add("Transcript is longer than a listening set should be (%d words).".formatted(words));
        }
    }

    private void validateQuestions(GeneratedExercise exercise, List<String> errors) {
        List<Question> questions = exercise.questions();

        if (questions.size() != REQUIRED_QUESTIONS) {
            errors.add("Exactly %d questions are required but %d were produced."
                    .formatted(REQUIRED_QUESTIONS, questions.size()));
        }

        Set<String> questionIds = new HashSet<>();
        Set<String> normalizedStems = new HashSet<>();

        for (Question question : questions) {
            String label = "Question " + question.id();

            if (isBlank(question.id()) || !questionIds.add(question.id())) {
                errors.add("Question ids must be present and unique.");
            }
            if (isBlank(question.stem())) {
                errors.add(label + ": stem must not be empty.");
            } else if (!normalizedStems.add(normalize(question.stem()))) {
                errors.add(label + ": duplicates another question.");
            }
            if (question.skill() == null) {
                errors.add(label + ": skill must be set.");
            }
            if (isBlank(question.explanation())) {
                errors.add(label + ": explanation must not be empty.");
            }
            if (isBlank(question.evidence())) {
                errors.add(label + ": evidence must not be empty.");
            }

            validateOptions(exercise, question, label, errors);
        }

        long distinctSkills =
                questions.stream().map(Question::skill).filter(java.util.Objects::nonNull).distinct().count();
        if (!questions.isEmpty() && distinctSkills < 3) {
            errors.add("The set must test at least three different skills, not repeat one.");
        }
    }

    private void validateOptions(GeneratedExercise exercise, Question question, String label, List<String> errors) {
        List<AnswerOption> options = question.options();

        if (options.size() != REQUIRED_OPTIONS) {
            errors.add("%s: exactly %d options are required but %d were produced."
                    .formatted(label, REQUIRED_OPTIONS, options.size()));
            return;
        }

        Set<String> optionIds = new HashSet<>();
        Set<String> optionTexts = new HashSet<>();
        for (AnswerOption option : options) {
            if (!optionIds.add(option.id())) {
                errors.add(label + ": option ids must be unique.");
            }
            if (!optionTexts.add(normalize(option.text()))) {
                errors.add(label + ": options must be distinct from one another.");
            }
        }
        if (!optionIds.equals(Set.of("A", "B", "C", "D"))) {
            errors.add(label + ": options must be labelled A, B, C, and D.");
        }

        if (question.correctOptionId() == null || !optionIds.contains(question.correctOptionId())) {
            errors.add(label + ": correctOptionId must name one of this question's options.");
            return;
        }

        // The answer has to be findable in what the listener actually hears.
        if (!isSupportedByTranscript(exercise, question)) {
            errors.add(label + ": the evidence does not appear in the transcript, so the answer is not supportable.");
        }
    }

    /**
     * A cheap, deterministic groundedness check: the distinctive words of the
     * stated evidence must actually occur in the dialogue. It cannot prove the
     * answer is right, but it reliably catches evidence the model invented.
     */
    private boolean isSupportedByTranscript(GeneratedExercise exercise, Question question) {
        String transcript = normalize(exercise.transcriptText());
        List<String> contentWords = List.of(normalize(question.evidence()).split("\\s+")).stream()
                .filter(word -> word.length() > 4)
                .distinct()
                .toList();

        if (contentWords.isEmpty()) {
            // Evidence made only of short words: too weak to check, not a failure.
            return true;
        }
        long present = contentWords.stream().filter(transcript::contains).count();
        return present * 2 >= contentWords.size();
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
