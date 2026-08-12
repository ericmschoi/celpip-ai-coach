package com.listenspeak.coach.speaking;

import com.listenspeak.coach.speaking.domain.SpeakingEvaluation;
import com.listenspeak.coach.speaking.domain.SpeakingPrompt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port. As with listening, the owner id is always the first
 * argument, so a cross-user read cannot be written by accident.
 */
public interface SpeakingRepository {

    void savePrompt(SpeakingPrompt prompt);

    Optional<SpeakingPrompt> findPromptByOwnerAndId(String ownerId, UUID promptId);

    void saveEvaluation(SpeakingEvaluation evaluation);

    Optional<SpeakingEvaluation> findEvaluationByOwnerAndId(String ownerId, UUID evaluationId);

    List<SpeakingEvaluation> findRecentEvaluations(String ownerId, int limit);
}
