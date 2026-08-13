package com.listenspeak.coach.platform.aws;

import com.listenspeak.coach.platform.config.AppProperties;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * AWS clients, created only when the app is actually configured to use AWS.
 *
 * <p>Credentials come from the default provider chain, which in Fargate means
 * the task role. No key material is ever read from configuration.
 *
 * <p>{@code AWS_ENDPOINT_URL} points these at LocalStack for integration tests;
 * it is unset in a real deployment.
 */
@Configuration
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "AWS")
public class AwsClientConfig {

    @Bean
    DynamoDbClient dynamoDbClient(AppProperties properties) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder().region(region());
        endpointOverride().ifPresent(builder::endpointOverride);
        return builder.build();
    }

    @Bean
    S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder().region(region());
        endpointOverride().ifPresent(endpoint -> builder.endpointOverride(endpoint)
                // LocalStack needs path-style addressing.
                .forcePathStyle(true));
        return builder.build();
    }

    @Bean
    S3Presigner s3Presigner() {
        S3Presigner.Builder builder = S3Presigner.builder().region(region());
        endpointOverride().ifPresent(builder::endpointOverride);
        return builder.build();
    }

    private static Region region() {
        String region = System.getenv("AWS_REGION");
        return region == null || region.isBlank() ? Region.CA_CENTRAL_1 : Region.of(region);
    }

    private static java.util.Optional<URI> endpointOverride() {
        String endpoint = System.getenv("AWS_ENDPOINT_URL");
        return endpoint == null || endpoint.isBlank()
                ? java.util.Optional.empty()
                : java.util.Optional.of(URI.create(endpoint));
    }
}
