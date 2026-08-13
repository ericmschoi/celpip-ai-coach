package com.listenspeak.coach.platform.limits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.listenspeak.coach.platform.config.AppProperties;
import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.platform.web.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The only thing standing between a bug or a stranger and an unbounded provider
 * bill.
 */
class UsageLimiterTest {

    private static final Instant NOON = Instant.parse("2026-08-11T12:00:00Z");

    private AppProperties properties(int perDayListening, int perDaySpeaking, int burstPerMinute) {
        return new AppProperties(
                AppProperties.ContentMode.SEED,
                new AppProperties.Auth(AppProperties.AuthMode.LOCAL_STUB, null, null, null),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Limits(perDayListening, perDaySpeaking, burstPerMinute),
                new AppProperties.Speaking(15_728_640, List.of("audio/webm"), false),
                new AppProperties.Storage(
                        AppProperties.StorageMode.LOCAL,
                        null,
                        null,
                        "./target/test-data",
                        Duration.ofMinutes(15),
                        Duration.ofDays(30)),
                new AppProperties.OpenAi(
                        null,
                        "https://api.openai.com/v1",
                        "g",
                        "s",
                        "t",
                        "x",
                        "whisper-1",
                        Duration.ofSeconds(60),
                        2));
    }

    private UsageLimiter limiterAt(Instant instant, AppProperties properties) {
        return new UsageLimiter(new InMemoryUsageCounter(), properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void allowsUsageUpToTheDailyCap() {
        UsageLimiter limiter = limiterAt(NOON, properties(3, 30, 100));

        for (int i = 0; i < 3; i++) {
            limiter.consume("user", LimitedAction.LISTENING_GENERATION);
        }

        assertThat(limiter.remainingToday("user", LimitedAction.LISTENING_GENERATION)).isZero();
    }

    @Test
    void rejectsTheRequestAfterTheDailyCap() {
        UsageLimiter limiter = limiterAt(NOON, properties(2, 30, 100));
        limiter.consume("user", LimitedAction.LISTENING_GENERATION);
        limiter.consume("user", LimitedAction.LISTENING_GENERATION);

        assertThatThrownBy(() -> limiter.consume("user", LimitedAction.LISTENING_GENERATION))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.DAILY_LIMIT_REACHED);
    }

    @Test
    void rejectsABurstBeforeTheDailyCapIsEvenReached() {
        UsageLimiter limiter = limiterAt(NOON, properties(100, 100, 2));
        limiter.consume("user", LimitedAction.LISTENING_GENERATION);
        limiter.consume("user", LimitedAction.LISTENING_GENERATION);

        assertThatThrownBy(() -> limiter.consume("user", LimitedAction.LISTENING_GENERATION))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void countsEachUserSeparately() {
        UsageLimiter limiter = limiterAt(NOON, properties(1, 30, 100));
        limiter.consume("first", LimitedAction.LISTENING_GENERATION);

        limiter.consume("second", LimitedAction.LISTENING_GENERATION);

        assertThat(limiter.remainingToday("second", LimitedAction.LISTENING_GENERATION)).isZero();
    }

    @Test
    void countsEachActionSeparately() {
        UsageLimiter limiter = limiterAt(NOON, properties(1, 5, 100));
        limiter.consume("user", LimitedAction.LISTENING_GENERATION);

        limiter.consume("user", LimitedAction.SPEAKING_EVALUATION);

        assertThat(limiter.remainingToday("user", LimitedAction.SPEAKING_EVALUATION)).isEqualTo(4);
    }

    @Test
    void countsByUtcDaySoTheCapDoesNotResetAtLocalMidnight() {
        AppProperties config = properties(5, 30, 100);
        UsageCounter counter = new InMemoryUsageCounter();

        UsageLimiter beforeMidnight =
                new UsageLimiter(counter, config, Clock.fixed(Instant.parse("2026-08-11T23:59:00Z"), ZoneOffset.UTC));
        UsageLimiter afterMidnight =
                new UsageLimiter(counter, config, Clock.fixed(Instant.parse("2026-08-12T00:01:00Z"), ZoneOffset.UTC));

        beforeMidnight.consume("user", LimitedAction.LISTENING_GENERATION);

        assertThat(afterMidnight.remainingToday("user", LimitedAction.LISTENING_GENERATION))
                .isEqualTo(5);
    }
}
