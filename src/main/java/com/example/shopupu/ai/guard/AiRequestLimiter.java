package com.example.shopupu.ai.guard;

import com.example.shopupu.config.AiProperties;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Bounded identity counters; capacity exhaustion rejects new clients instead of resetting existing quotas. */
@Component
public class AiRequestLimiter {
    private final AiProperties properties;
    private final Clock clock;
    private final Map<String, Counter> clients = new HashMap<>();

    @Autowired
    public AiRequestLimiter(AiProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AiRequestLimiter(AiProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized void check(String peerAddress, String account) {
        long now = clock.instant().getEpochSecond();
        List<String> keys = account == null
                ? List.of("ip:" + peerAddress) : List.of("ip:" + peerAddress, "account:" + account);
        long missing = keys.stream().filter(key -> !clients.containsKey(key)).count();
        if (clients.size() + missing > properties.getMaxTrackedClients()) {
            clients.values().removeIf(counter -> counter.day != now / 86400);
            missing = keys.stream().filter(key -> !clients.containsKey(key)).count();
            if (clients.size() + missing > properties.getMaxTrackedClients()) {
                throw new AiRateLimitException(Math.max(1, 86400 - now % 86400));
            }
        }
        for (String key : keys) {
            Counter counter = clients.computeIfAbsent(key, ignored -> new Counter());
            counter.reset(now);
            if (counter.dayRequests >= properties.getRequestsPerDay()) {
                throw new AiRateLimitException(Math.max(1, 86400 - now % 86400));
            }
            if (counter.minuteRequests >= properties.getRequestsPerMinute()) {
                throw new AiRateLimitException(Math.max(1, 60 - now % 60));
            }
        }
        keys.forEach(key -> {
            Counter counter = clients.get(key);
            counter.minuteRequests++;
            counter.dayRequests++;
        });
    }

    private static final class Counter {
        private long minute = -1;
        private long day = -1;
        private int minuteRequests;
        private int dayRequests;

        void reset(long now) {
            if (minute != now / 60) {
                minute = now / 60;
                minuteRequests = 0;
            }
            if (day != now / 86400) {
                day = now / 86400;
                dayRequests = 0;
            }
        }
    }
}
