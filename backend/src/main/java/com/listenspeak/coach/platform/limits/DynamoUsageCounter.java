package com.listenspeak.coach.platform.limits;

import com.listenspeak.coach.platform.aws.SingleTable;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/**
 * Durable daily usage tally, using a DynamoDB atomic counter.
 *
 * <p>An atomic {@code ADD} is what makes the cap correct under concurrency and
 * across restarts, which is exactly what the in-process burst limiter cannot
 * offer. The item carries a TTL a few days out so old counters clean themselves
 * up.
 */
@Component
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "AWS")
public class DynamoUsageCounter implements UsageCounter {

    private static final String COUNT = "count";
    private static final long RETENTION_DAYS = 7;

    private final SingleTable table;

    public DynamoUsageCounter(SingleTable table) {
        this.table = table;
    }

    @Override
    public int incrementAndGet(String userId, LimitedAction action, LocalDate day) {
        var response = table.client()
                .updateItem(UpdateItemRequest.builder()
                        .tableName(table.tableName())
                        .key(key(userId, action, day))
                        .attributeUpdates(Map.of(
                                COUNT,
                                AttributeValueUpdate.builder()
                                        .action(software.amazon.awssdk.services.dynamodb.model.AttributeAction.ADD)
                                        .value(number(1))
                                        .build(),
                                SingleTable.TTL,
                                AttributeValueUpdate.builder()
                                        .action(software.amazon.awssdk.services.dynamodb.model.AttributeAction.PUT)
                                        .value(number(day.plusDays(RETENTION_DAYS)
                                                .atStartOfDay(ZoneOffset.UTC)
                                                .toEpochSecond()))
                                        .build()))
                        .returnValues(ReturnValue.UPDATED_NEW)
                        .build());

        AttributeValue count = response.attributes().get(COUNT);
        return count == null ? 1 : Integer.parseInt(count.n());
    }

    @Override
    public int current(String userId, LimitedAction action, LocalDate day) {
        Map<String, AttributeValue> item = table.client()
                .getItem(GetItemRequest.builder()
                        .tableName(table.tableName())
                        .key(key(userId, action, day))
                        .consistentRead(true)
                        .build())
                .item();

        AttributeValue count = item == null ? null : item.get(COUNT);
        return count == null ? 0 : Integer.parseInt(count.n());
    }

    private static Map<String, AttributeValue> key(String userId, LimitedAction action, LocalDate day) {
        return Map.of(
                SingleTable.PK,
                AttributeValue.builder().s(SingleTable.partitionKey(userId)).build(),
                SingleTable.SK,
                AttributeValue.builder()
                        .s("USAGE#%s#%s".formatted(day, action.name()))
                        .build());
    }

    private static AttributeValue number(long value) {
        return AttributeValue.builder().n(Long.toString(value)).build();
    }
}
