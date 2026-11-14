package com.azentio.doorstep.signals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Needs a live Redis on localhost:6379 (docker compose up redis). Run with: mvn test -Pintegration */
@Tag("integration")
class RedisSignalStoreIntegrationTest {

    @Test
    void trimsMembersOlderThanTheWindow() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        try {
            StringRedisTemplate template = new StringRedisTemplate(factory);
            RedisSignalStore store = new RedisSignalStore(template);
            String phone = "9" + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000_000L);
            Instant now = Instant.now();

            store.record(SignalType.PHONE_PANS, phone, "old-pan", now.minus(Duration.ofDays(31)), Duration.ofDays(30));
            store.record(SignalType.PHONE_PANS, phone, "new-pan", now, Duration.ofDays(30));

            assertThat(store.membersSince(SignalType.PHONE_PANS, phone, now.minus(Duration.ofDays(30))))
                    .containsExactly("new-pan");
        } finally {
            factory.destroy();
        }
    }
}
