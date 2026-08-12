package com.listenspeak.coach.speaking;

import com.listenspeak.coach.speaking.domain.SpeakingEvaluation;
import com.listenspeak.coach.speaking.domain.SpeakingPrompt;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** Local-mode repository. Expired prompts are filtered on read, mirroring DynamoDB TTL. */
@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "LOCAL", matchIfMissing = true)
public class InMemorySpeakingRepository implements SpeakingRepository {

    private final Map<String, SpeakingPrompt> prompts = new ConcurrentHashMap<>();
    private final Map<String, SpeakingEvaluation> evaluations = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemorySpeakingRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void savePrompt(SpeakingPrompt prompt) {
        prompts.put(key(prompt.ownerId(), prompt.id()), prompt);
    }

    @Override
    public Optional<SpeakingPrompt> findPromptByOwnerAndId(String ownerId, UUID promptId) {
        return Optional.ofNullable(prompts.get(key(ownerId, promptId)))
                .filter(prompt -> prompt.expiresAt() == null
                        || prompt.expiresAt().isAfter(clock.instant()));
    }

    @Override
    public void saveEvaluation(SpeakingEvaluation evaluation) {
        evaluations.put(key(evaluation.ownerId(), evaluation.id()), evaluation);
    }

    @Override
    public Optional<SpeakingEvaluation> findEvaluationByOwnerAndId(String ownerId, UUID evaluationId) {
        return Optional.ofNullable(evaluations.get(key(ownerId, evaluationId)));
    }

    @Override
    public List<SpeakingEvaluation> findRecentEvaluations(String ownerId, int limit) {
        return evaluations.values().stream()
                .filter(evaluation -> evaluation.ownerId().equals(ownerId))
                .sorted(Comparator.comparing(SpeakingEvaluation::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    private static String key(String ownerId, UUID id) {
        return ownerId + "/" + id;
    }
}
