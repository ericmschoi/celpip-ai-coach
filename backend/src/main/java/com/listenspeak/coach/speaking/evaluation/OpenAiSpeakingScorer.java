package com.listenspeak.coach.speaking.evaluation;

import com.listenspeak.coach.listening.generation.StructuredResponses;
import com.listenspeak.coach.platform.config.AppProperties;
import com.listenspeak.coach.platform.config.AppProperties.ContentMode;
import com.listenspeak.coach.platform.openai.OpenAiConfiguredCondition;
import com.listenspeak.coach.platform.openai.OpenAiErrors;
import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.platform.web.ErrorCode;
import com.listenspeak.coach.speaking.SpeakingTaskCatalog.SpeakingTask;
import com.listenspeak.coach.speaking.domain.DeliveryMetrics;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation.Confidence;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation.Correction;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation.Dimension;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation.DimensionScore;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation.Improvement;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** Live evaluation through the Responses API with strict Structured Outputs. */
@Component
@Conditional(OpenAiConfiguredCondition.class)
public class OpenAiSpeakingScorer implements SpeakingScorer {

    private static final long MAX_OUTPUT_TOKENS = 4_000;

    private record AssessmentDocument(
            Integer estimatedLevel,
            String confidence,
            List<DimensionDocument> dimensions,
            List<String> strengths,
            List<ImprovementDocument> improvements,
            List<CorrectionDocument> corrections,
            String sampleAnswer,
            String nextDrill) {}

    private record DimensionDocument(String dimension, Integer score, String evidence) {}

    private record ImprovementDocument(String issue, String whyItMatters, String howToFix) {}

    private record CorrectionDocument(String original, String improved, String reason) {}

    private final OpenAIClient client;
    private final StructuredResponses structuredResponses;
    private final AppProperties properties;

    public OpenAiSpeakingScorer(
            OpenAIClient client, StructuredResponses structuredResponses, AppProperties properties) {
        this.client = client;
        this.structuredResponses = structuredResponses;
        this.properties = properties;
    }

    @Override
    public ContentMode mode() {
        return ContentMode.LIVE;
    }

    @Override
    public Assessment score(
            SpeakingTask task,
            String promptText,
            String transcript,
            boolean transcriptAvailable,
            DeliveryMetrics metrics) {

        if (!transcriptAvailable || transcript.isBlank()) {
            // The live scorer judges language from a transcript. Without one
            // there is nothing to judge, and guessing is not an option.
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "There was no speech to assess in that recording.");
        }

        var request = new StructuredResponses.Request(
                properties.openai().scoringModel(),
                ScoringPrompts.systemPrompt(),
                ScoringPrompts.userPrompt(task, promptText, transcript, metrics),
                ScoringPrompts.SCHEMA_NAME,
                ScoringPrompts.jsonSchema(),
                MAX_OUTPUT_TOKENS);

        AssessmentDocument document;
        try {
            Response response = client.responses().create(structuredResponses.toParams(request));
            document = structuredResponses.parse(response, AssessmentDocument.class, "speaking evaluation");
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw OpenAiErrors.translate("speaking evaluation", e);
        }

        return toAssessment(document);
    }

    private Assessment toAssessment(AssessmentDocument document) {
        try {
            return new Assessment(
                    document.estimatedLevel(),
                    document.confidence() == null ? Confidence.LOW : Confidence.valueOf(document.confidence()),
                    document.dimensions() == null
                            ? List.of()
                            : document.dimensions().stream()
                                    .map(dimension -> new DimensionScore(
                                            Dimension.valueOf(dimension.dimension()),
                                            dimension.score(),
                                            dimension.evidence()))
                                    .toList(),
                    document.strengths() == null ? List.of() : document.strengths(),
                    document.improvements() == null
                            ? List.of()
                            : document.improvements().stream()
                                    .map(improvement -> new Improvement(
                                            improvement.issue(), improvement.whyItMatters(), improvement.howToFix()))
                                    .toList(),
                    document.corrections() == null
                            ? List.of()
                            : document.corrections().stream()
                                    .map(correction -> new Correction(
                                            correction.original(), correction.improved(), correction.reason()))
                                    .toList(),
                    document.sampleAnswer(),
                    document.nextDrill(),
                    "model:" + properties.openai().scoringModel() + "/" + ScoringPrompts.VERSION);

        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException(
                    ErrorCode.GENERATION_INVALID, "The evaluation came back in an unusable form. Try again.", e);
        }
    }
}
