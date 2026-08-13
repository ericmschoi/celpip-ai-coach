package com.listenspeak.coach.speaking;

import com.listenspeak.coach.platform.aws.SingleTable;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation;
import com.listenspeak.coach.speaking.domain.SpeakingPrompt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * DynamoDB single-table repository.
 *
 * <pre>
 *   SPEAKING_PROMPT#{promptId}                        TTL: yes
 *   SPEAKING_EVALUATION#{evaluationId}                TTL: no
 *   SPEAKING_HISTORY#{createdAt}#{evaluationId}       TTL: no
 * </pre>
 */
@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "AWS")
public class DynamoSpeakingRepository implements SpeakingRepository {

    private final SingleTable table;

    public DynamoSpeakingRepository(SingleTable table) {
        this.table = table;
    }

    @Override
    public void savePrompt(SpeakingPrompt prompt) {
        table.put(prompt.ownerId(), "SPEAKING_PROMPT#" + prompt.id(), prompt, prompt.expiresAt());
    }

    @Override
    public Optional<SpeakingPrompt> findPromptByOwnerAndId(String ownerId, UUID promptId) {
        return table.get(ownerId, "SPEAKING_PROMPT#" + promptId, SpeakingPrompt.class);
    }

    @Override
    public void saveEvaluation(SpeakingEvaluation evaluation) {
        table.put(evaluation.ownerId(), "SPEAKING_EVALUATION#" + evaluation.id(), evaluation, null);
        table.put(
                evaluation.ownerId(),
                "SPEAKING_HISTORY#%s#%s".formatted(evaluation.createdAt(), evaluation.id()),
                evaluation,
                null);
    }

    @Override
    public Optional<SpeakingEvaluation> findEvaluationByOwnerAndId(String ownerId, UUID evaluationId) {
        return table.get(ownerId, "SPEAKING_EVALUATION#" + evaluationId, SpeakingEvaluation.class);
    }

    @Override
    public List<SpeakingEvaluation> findRecentEvaluations(String ownerId, int limit) {
        return table.queryByPrefix(ownerId, "SPEAKING_HISTORY#", limit, SpeakingEvaluation.class);
    }
}
