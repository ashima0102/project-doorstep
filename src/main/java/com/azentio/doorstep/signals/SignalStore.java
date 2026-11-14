package com.azentio.doorstep.signals;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Time-windowed sets of members per identifier, e.g. "which PANs used this phone".
 * Adding a member that already exists only refreshes its timestamp, so recording is idempotent.
 */
public interface SignalStore {

    /** Links {@code member} to {@code key} at {@code at}; the key may be dropped once {@code retention} has passed. */
    void record(SignalType type, String key, String member, Instant at, Duration retention);

    /** Members recorded at or after {@code since}. Older members are trimmed and never counted. */
    Set<String> membersSince(SignalType type, String key, Instant since);
}
