package com.orderhub.backend.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttemptLimiterTest {

    /** Reloj manual para avanzar el tiempo sin dormir. */
    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MutableClock clock;
    private AttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        limiter = new AttemptLimiter(new SecurityProperties(3, 10), clock);
    }

    private void fail(String key, int times) {
        for (int i = 0; i < times; i++) {
            limiter.checkAllowed(key);
            limiter.recordFailure(key);
        }
    }

    @Test
    void allowsUpToMaxFailures_thenBlocksWithRetryAfter() {
        fail("k", 3);

        assertThatThrownBy(() -> limiter.checkAllowed("k"))
                .isInstanceOf(TooManyAttemptsException.class)
                .satisfies(e -> assertThat(((TooManyAttemptsException) e).getRetryAfterSeconds()).isEqualTo(600));
    }

    @Test
    void retryAfterShrinksAsTimePasses() {
        fail("k", 3);
        clock.advance(Duration.ofMinutes(4));

        assertThatThrownBy(() -> limiter.checkAllowed("k"))
                .satisfies(e -> assertThat(((TooManyAttemptsException) e).getRetryAfterSeconds()).isEqualTo(360));
    }

    @Test
    void unblocksAfterTheWindow() {
        fail("k", 3);
        clock.advance(Duration.ofMinutes(10));

        assertThatCode(() -> limiter.checkAllowed("k")).doesNotThrowAnyException();
    }

    @Test
    void oldFailuresSlideOutOfTheWindow() {
        fail("k", 2);
        clock.advance(Duration.ofMinutes(6));
        fail("k", 1);   // 3 fallos en total, pero los 2 primeros vencen en 4 min
        clock.advance(Duration.ofMinutes(5));

        assertThatCode(() -> limiter.checkAllowed("k")).doesNotThrowAnyException();
    }

    @Test
    void resetClearsTheCounter() {
        fail("k", 3);
        limiter.reset("k");

        assertThatCode(() -> fail("k", 3)).doesNotThrowAnyException();
    }

    @Test
    void keysAreIndependent() {
        fail("a", 3);

        assertThatCode(() -> limiter.checkAllowed("b")).doesNotThrowAnyException();
    }

    @Test
    void expiredKeysAreNotKept() {
        fail("a", 1);
        clock.advance(Duration.ofMinutes(11));
        limiter.checkAllowed("a");

        assertThat(limiter.trackedKeys()).isZero();
    }

    @Test
    void invalidConfigFallsBackToDefaults() {
        SecurityProperties p = new SecurityProperties(0, 0);

        assertThat(p.loginMaxAttempts()).isEqualTo(5);
        assertThat(p.loginWindowMinutes()).isEqualTo(15);
    }
}
