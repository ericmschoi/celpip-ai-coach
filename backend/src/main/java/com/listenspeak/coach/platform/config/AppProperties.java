package com.listenspeak.coach.platform.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Every tunable the application has, in one place. Nothing outside this class
 * reads {@code System.getenv} or scatters literals such as answer times.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull ContentMode contentMode,
        @Valid @NotNull Auth auth,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Limits limits,
        @Valid @NotNull Speaking speaking,
        @Valid @NotNull Storage storage,
        @Valid @NotNull OpenAi openai) {

    /** Where exercise and evaluation content comes from. */
    public enum ContentMode {
        /** Deterministic local fixtures. No provider calls, no cost. */
        SEED,
        /** Real OpenAI calls. */
        LIVE
    }

    public enum AuthMode {
        /** Local/e2e only: trusts a dev header instead of a real token. */
        LOCAL_STUB,
        /** Validates Cognito-issued JWTs. */
        COGNITO
    }

    public enum StorageMode {
        /** Filesystem + in-memory repositories under {@code localPath}. */
        LOCAL,
        /** Real DynamoDB and S3. */
        AWS
    }

    public record Auth(@NotNull AuthMode mode, String issuerUri, String userPoolId, String clientId) {

        public Auth {
            if (mode == AuthMode.COGNITO && (issuerUri == null || issuerUri.isBlank())) {
                throw new IllegalArgumentException("app.auth.issuer-uri is required when mode=COGNITO");
            }
        }
    }

    public record Cors(@NotEmpty List<String> allowedOrigins) {}

    public record Limits(
            @Min(1) int listeningPerDay,
            @Min(1) int speakingPerDay,
            @Min(1) int burstPerMinute) {}

    public record Speaking(
            @Min(1024) long maxUploadBytes,
            @NotEmpty List<String> allowedContentTypes,
            /** Recordings are deleted after evaluation unless this is set. */
            boolean retainRecordings) {}

    public record Storage(
            @NotNull StorageMode mode,
            String dynamodbTable,
            String audioBucket,
            @NotNull String localPath,
            /** Lifetime of presigned GET URLs handed to the browser. */
            @NotNull Duration presignedUrlTtl,
            /** DynamoDB TTL for generated exercises. */
            @NotNull Duration exerciseTtl) {

        public Storage {
            if (mode == StorageMode.AWS) {
                if (dynamodbTable == null || dynamodbTable.isBlank()) {
                    throw new IllegalArgumentException("app.storage.dynamodb-table is required when mode=AWS");
                }
                if (audioBucket == null || audioBucket.isBlank()) {
                    throw new IllegalArgumentException("app.storage.audio-bucket is required when mode=AWS");
                }
            }
        }
    }

    /**
     * Model names are configuration, never literals in adapter code, so a model
     * can be swapped by environment variable with no code change.
     */
    public record OpenAi(
            String apiKey,
            @NotNull String baseUrl,
            @NotNull String generationModel,
            @NotNull String scoringModel,
            @NotNull String ttsModel,
            @NotNull String transcriptionModel,
            /** Timing-analysis model. Only whisper-1 returns word timestamps; blank disables timing. */
            String timingModel,
            @NotNull Duration requestTimeout,
            @Min(0) int maxRetries) {

        /** True when live provider calls are actually possible. */
        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
