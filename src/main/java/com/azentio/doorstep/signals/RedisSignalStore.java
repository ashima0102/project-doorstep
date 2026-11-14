package com.azentio.doorstep.signals;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * One sorted set per identifier, scored by submittedAt epoch millis. Reads trim everything older
 * than the window (ZREMRANGEBYSCORE) before counting (ZRANGEBYSCORE), so stale links never count.
 * Keys hold a SHA-256 of the identifier, never the raw PAN or phone.
 */
@Component
@ConditionalOnProperty(name = "doorstep.signals.store", havingValue = "redis", matchIfMissing = true)
public class RedisSignalStore implements SignalStore {

    private final StringRedisTemplate redis;

    public RedisSignalStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void record(SignalType type, String key, String member, Instant at, Duration retention) {
        String redisKey = key(type, key);
        redis.opsForZSet().add(redisKey, member, at.toEpochMilli());
        redis.expire(redisKey, retention);
    }

    @Override
    public Set<String> membersSince(SignalType type, String key, Instant since) {
        String redisKey = key(type, key);
        double floor = since.toEpochMilli();
        redis.opsForZSet().removeRangeByScore(redisKey, Double.NEGATIVE_INFINITY, Math.nextDown(floor));
        Set<String> members = redis.opsForZSet().rangeByScore(redisKey, floor, Double.POSITIVE_INFINITY);
        return members == null ? Set.of() : members;
    }

    private static String key(SignalType type, String identifier) {
        return "signal:" + type.name().toLowerCase() + ":" + Hashing.sha256(identifier);
    }
}
