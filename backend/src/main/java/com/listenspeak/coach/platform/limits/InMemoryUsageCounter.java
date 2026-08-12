package com.listenspeak.coach.platform.limits;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Local-mode counter. The AWS profile uses a DynamoDB-backed counter instead. */
@Component
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "LOCAL", matchIfMissing = true)
public class InMemoryUsageCounter implements UsageCounter {

    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    @Override
    public int incrementAndGet(String userId, LimitedAction action, LocalDate day) {
        return counts.computeIfAbsent(key(userId, action, day), ignored -> new AtomicInteger())
                .incrementAndGet();
    }

    @Override
    public int current(String userId, LimitedAction action, LocalDate day) {
        AtomicInteger count = counts.get(key(userId, action, day));
        return count == null ? 0 : count.get();
    }

    private static String key(String userId, LimitedAction action, LocalDate day) {
        return "%s|%s|%s".formatted(userId, action, day);
    }
}
