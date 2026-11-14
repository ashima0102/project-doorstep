package com.azentio.doorstep.signals;

import com.azentio.doorstep.TestFixtures;
import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.azentio.doorstep.rules.PhoneSharedRule;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SignalWindowTest {

    private final Clock clock = Clock.fixed(TestFixtures.NOW, ZoneOffset.UTC);
    private final InMemorySignalStore store = new InMemorySignalStore();
    private final SignalCollector collector = new SignalCollector(store, clock, TestFixtures.defaultProperties());
    private final PhoneSharedRule phoneRule = new PhoneSharedRule(TestFixtures.defaultProperties());

    private ApplicationRequest onSharedPhone(String id, String pan, Instant submittedAt) {
        return TestFixtures.clean().id(id).pan(pan).device("dev-" + id).submittedAt(submittedAt).build();
    }

    private boolean phoneSharedFiresFor(ApplicationRequest app) {
        boolean fired = phoneRule.evaluate(app, collector.collect(app)).isPresent();
        collector.record(app);
        return fired;
    }

    @Test
    void thirdDistinctPanOnPhoneInsideThirtyDaysFires() {
        Instant tenDaysAgo = TestFixtures.NOW.minus(Duration.ofDays(10));
        assertThat(phoneSharedFiresFor(onSharedPhone("A1", "AAAAA1111A", tenDaysAgo))).isFalse();
        assertThat(phoneSharedFiresFor(onSharedPhone("A2", "BBBBB2222B", tenDaysAgo))).isFalse();
        assertThat(phoneSharedFiresFor(onSharedPhone("A3", "CCCCC3333C", TestFixtures.NOW))).isTrue();
    }

    @Test
    void doesNotFireWhenEarlierApplicationsAreOlderThanThirtyDays() {
        Instant thirtyOneDaysAgo = TestFixtures.NOW.minus(Duration.ofDays(31));
        collector.record(onSharedPhone("A1", "AAAAA1111A", thirtyOneDaysAgo));
        collector.record(onSharedPhone("A2", "BBBBB2222B", thirtyOneDaysAgo));
        assertThat(phoneSharedFiresFor(onSharedPhone("A3", "CCCCC3333C", TestFixtures.NOW))).isFalse();
    }

    @Test
    void samePanRepeatedOnPhoneCountsOnce() {
        assertThat(phoneSharedFiresFor(onSharedPhone("A1", "AAAAA1111A", TestFixtures.NOW))).isFalse();
        assertThat(phoneSharedFiresFor(onSharedPhone("A2", "AAAAA1111A", TestFixtures.NOW))).isFalse();
        assertThat(phoneSharedFiresFor(onSharedPhone("A3", "BBBBB2222B", TestFixtures.NOW))).isFalse();
    }

    @Test
    void panReuseIgnoresTheApplicationItself() {
        ApplicationRequest app = onSharedPhone("A1", "AAAAA1111A", TestFixtures.NOW);
        collector.record(app);
        assertThat(collector.collect(app).otherIdentitiesOnPan()).isEmpty();
    }
}
