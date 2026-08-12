package com.listenspeak.coach.platform.aws;

import com.listenspeak.coach.platform.config.AppProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * Thin access layer over the single DynamoDB table.
 *
 * <p>Domain objects are stored as a JSON {@code payload} attribute rather than
 * mapped attribute-by-attribute. The access patterns are all "fetch the whole
 * item for one user", so per-attribute mapping would buy nothing and would mean
 * hand-writing a schema for every nested record. The trade-off is that
 * DynamoDB cannot filter on a field inside the payload, which no access pattern
 * needs.
 *
 * <p>Every method takes the owner id, and the partition key is derived from it,
 * so a cross-user read cannot be expressed.
 */
@Component
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "AWS")
public class SingleTable {

    public static final String PK = "pk";
    public static final String SK = "sk";
    public static final String PAYLOAD = "payload";
    public static final String TTL = "expiresAt";

    private final DynamoDbClient dynamo;
    private final ObjectMapper objectMapper;
    private final String tableName;

    public SingleTable(DynamoDbClient dynamo, ObjectMapper objectMapper, AppProperties properties) {
        this.dynamo = dynamo;
        this.objectMapper = objectMapper;
        this.tableName = properties.storage().dynamodbTable();
    }

    public static String partitionKey(String ownerId) {
        return "USER#" + ownerId;
    }

    public void put(String ownerId, String sortKey, Object value, Instant expiresAt) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PK, string(partitionKey(ownerId)));
        item.put(SK, string(sortKey));
        item.put(PAYLOAD, string(objectMapper.writeValueAsString(value)));

        if (expiresAt != null) {
            item.put(TTL, number(expiresAt.getEpochSecond()));
        }

        dynamo.putItem(PutItemRequest.builder().tableName(tableName).item(item).build());
    }

    public <T> Optional<T> get(String ownerId, String sortKey, Class<T> type) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of(PK, string(partitionKey(ownerId)), SK, string(sortKey)))
                        .consistentRead(true)
                        .build())
                .item();

        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        // TTL deletion is eventual, so an expired item may still be present.
        if (isExpired(item)) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(item.get(PAYLOAD).s(), type));
    }

    /** Newest first, because every listing in this app is "what did I do recently". */
    public <T> List<T> queryByPrefix(String ownerId, String sortKeyPrefix, int limit, Class<T> type) {
        var response = dynamo.query(QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("#pk = :pk AND begins_with(#sk, :prefix)")
                .expressionAttributeNames(Map.of("#pk", PK, "#sk", SK))
                .expressionAttributeValues(
                        Map.of(":pk", string(partitionKey(ownerId)), ":prefix", string(sortKeyPrefix)))
                .scanIndexForward(false)
                .limit(limit)
                .build());

        List<T> results = new ArrayList<>(response.items().size());
        for (Map<String, AttributeValue> item : response.items()) {
            if (!isExpired(item)) {
                results.add(objectMapper.readValue(item.get(PAYLOAD).s(), type));
            }
        }
        return List.copyOf(results);
    }

    private static boolean isExpired(Map<String, AttributeValue> item) {
        AttributeValue ttl = item.get(TTL);
        return ttl != null && Long.parseLong(ttl.n()) < Instant.now().getEpochSecond();
    }

    private static AttributeValue string(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue number(long value) {
        return AttributeValue.builder().n(Long.toString(value)).build();
    }

    public DynamoDbClient client() {
        return dynamo;
    }

    public String tableName() {
        return tableName;
    }
}
