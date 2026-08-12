package com.listenspeak.coach.listening.audio;

import com.listenspeak.coach.platform.config.AppProperties;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Private S3 storage with short-lived presigned reads.
 *
 * <p>Objects are never public: the bucket blocks all public access, and the
 * only way a browser reaches one is a presigned GET this service issues to the
 * authenticated owner.
 */
@Component
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "AWS")
public class S3AudioStorage implements AudioStorage {

    private static final Logger log = LoggerFactory.getLogger(S3AudioStorage.class);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public S3AudioStorage(S3Client s3, S3Presigner presigner, AppProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = properties.storage().audioBucket();
    }

    @Override
    public void storeIfAbsent(String key, byte[] content, String contentType) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return;
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            // Missing (404), or head not permitted: either way, write it.
        }
        store(key, content, contentType);
    }

    @Override
    public void store(String key, byte[] content, String contentType) {
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .serverSideEncryption(ServerSideEncryption.AES256)
                        .build(),
                RequestBody.fromBytes(content));

        log.debug("Stored audio key={} bytes={}", key, content.length);
    }

    @Override
    public String presignedUrl(String key, Duration ttl) {
        return presigner
                .presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .build())
                        .build())
                .url()
                .toString();
    }

    @Override
    public Optional<byte[]> read(String key) {
        try {
            return Optional.of(s3.getObjectAsBytes(
                            GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(builder -> builder.bucket(bucket).key(key));
    }
}
