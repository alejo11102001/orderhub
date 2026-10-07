package com.orderhub.backend.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de intentos fallidos de ventana deslizante, en memoria y por instancia.
 * Cada clave (p. ej. "login:IP:email") admite {@code maxAttempts} fallos dentro de la ventana.
 */
@Component
public class AttemptLimiter {

    private static final int PURGE_THRESHOLD = 10_000;

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    @Autowired
    public AttemptLimiter(SecurityProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AttemptLimiter(SecurityProperties properties, Clock clock) {
        this.maxAttempts = properties.loginMaxAttempts();
        this.window = Duration.ofMinutes(properties.loginWindowMinutes());
        this.clock = clock;
    }

    /** Lanza {@link TooManyAttemptsException} si la clave ya agotó sus intentos. */
    public void checkAllowed(String key) {
        long now = clock.millis();
        long[] retryAfterMillis = {0};
        failures.computeIfPresent(key, (k, deque) -> {
            prune(deque, now);
            if (deque.size() >= maxAttempts) {
                retryAfterMillis[0] = deque.peekFirst() + window.toMillis() - now;
            }
            return deque.isEmpty() ? null : deque;
        });
        if (retryAfterMillis[0] > 0) {
            throw new TooManyAttemptsException(Math.max(1, (retryAfterMillis[0] + 999) / 1000));
        }
    }

    public void recordFailure(String key) {
        long now = clock.millis();
        failures.compute(key, (k, deque) -> {
            Deque<Long> d = deque != null ? deque : new ArrayDeque<>();
            prune(d, now);
            d.addLast(now);
            return d;
        });
        if (failures.size() > PURGE_THRESHOLD) {
            purgeExpired(now);
        }
    }

    public void reset(String key) {
        failures.remove(key);
    }

    int trackedKeys() {
        return failures.size();
    }

    private void purgeExpired(long now) {
        for (String key : failures.keySet()) {
            // computeIfPresent es atómico por clave, igual que las demás operaciones
            failures.computeIfPresent(key, (k, deque) -> {
                prune(deque, now);
                return deque.isEmpty() ? null : deque;
            });
        }
    }

    private void prune(Deque<Long> deque, long now) {
        long cutoff = now - window.toMillis();
        while (!deque.isEmpty() && deque.peekFirst() <= cutoff) {
            deque.removeFirst();
        }
    }
}
