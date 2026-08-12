package com.listenspeak.coach.platform.limits;

import com.listenspeak.coach.platform.config.AppProperties;
import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.platform.web.ErrorCode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Two ceilings on every expensive operation: a short burst limit, and a daily
 * cap.
 *
 * <p><strong>Known limitation:</strong> the burst limiter is in-process. With
 * the single Fargate task this app deploys, that is exact; if the service is
 * ever scaled to two tasks each gets its own bucket and the effective burst
 * limit doubles. The daily cap goes through {@link UsageCounter}, which is
 * durable, so the expensive ceiling stays correct regardless. Documented in
 * docs/security.md.
 */
@Component
public class UsageLimiter {

    private static final Logger log = LoggerFactory.getLogger(UsageLimiter.class);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final UsageCounter usageCounter;
    private final AppProperties properties;
    private final Clock clock;

    public UsageLimiter(UsageCounter usageCounter, AppProperties properties, Clock clock) {
        this.usageCounter = usageCounter;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Consumes one unit of the user's allowance, or throws. Call this
     * <em>before</em> doing anything that costs money.
     */
    public void consume(String userId, LimitedAction action) {
        if (!bucketFor(userId, action).tryConsume(1)) {
            throw new ApiException(
                    ErrorCode.RATE_LIMITED,
                    "That is a lot of requests in a short time. Wait a few seconds and try again.");
        }

        int dailyLimit = dailyLimitFor(action);
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        int used = usageCounter.incrementAndGet(userId, action, today);

        if (used > dailyLimit) {
            log.info("Daily cap reached action={} used={} limit={}", action, used, dailyLimit);
            throw new ApiException(
                    ErrorCode.DAILY_LIMIT_REACHED,
                    "You have used all %d %s requests for today.".formatted(dailyLimit, action.label()));
        }

        log.debug("Usage action={} used={}/{}", action, used, dailyLimit);
    }

    public int remainingToday(String userId, LimitedAction action) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        return Math.max(0, dailyLimitFor(action) - usageCounter.current(userId, action, today));
    }

    private int dailyLimitFor(LimitedAction action) {
        return switch (action) {
            case LISTENING_GENERATION -> properties.limits().listeningPerDay();
            case SPEAKING_EVALUATION -> properties.limits().speakingPerDay();
        };
    }

    private Bucket bucketFor(String userId, LimitedAction action) {
        return buckets.computeIfAbsent(userId + "|" + action, ignored -> Bucket.builder()
                .addLimit(Bandwidth.classic(
                        properties.limits().burstPerMinute(),
                        Refill.intervally(properties.limits().burstPerMinute(), Duration.ofMinutes(1))))
                .build());
    }
}
