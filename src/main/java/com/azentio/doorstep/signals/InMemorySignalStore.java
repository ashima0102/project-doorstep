package com.azentio.doorstep.signals;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Same semantics as the Redis store, for offline tests and running without Redis (doorstep.signals.store=memory). */
@Component
@ConditionalOnProperty(name = "doorstep.signals.store", havingValue = "memory")
public class InMemorySignalStore implements SignalStore {

    private final Map<String, Map<String, Instant>> sets = new HashMap<>();

    @Override
    public synchronized void record(SignalType type, String key, String member, Instant at, Duration retention) {
        sets.computeIfAbsent(type + ":" + key, k -> new HashMap<>()).put(member, at);
    }

    @Override
    public synchronized Set<String> membersSince(SignalType type, String key, Instant since) {
        Map<String, Instant> members = sets.getOrDefault(type + ":" + key, new HashMap<>());
        members.values().removeIf(at -> at.isBefore(since));
        return members.keySet().stream().collect(Collectors.toUnmodifiableSet());
    }

    public synchronized void clear() {
        sets.clear();
    }
}
