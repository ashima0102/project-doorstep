package com.azentio.doorstep.service;

import com.azentio.doorstep.TestFixtures;
import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.azentio.doorstep.persistence.RuleHitRepository;
import com.azentio.doorstep.signals.SignalCollector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class IdempotencyTest {

    @Autowired
    ApplicationEvaluationService service;
    @Autowired
    RuleHitRepository ruleHits;
    @Autowired
    SignalCollector signals;

    @Test
    void exactRepeatIsSkippedWithoutTouchingRuleHitsOrSignals() {
        ApplicationRequest app = TestFixtures.clean().id("IDEM-1").pan("IDEMP1111A").phone("9333333333")
                .device("dev-idem").verified(false).submittedAt(Instant.now()).build();

        EvaluationOutcome first = service.evaluate(app);
        var signalsAfterFirst = signals.collect(app);
        EvaluationOutcome second = service.evaluate(app);

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(second.response()).isEqualTo(first.response());
        assertThat(ruleHits.countByApplicationId("IDEM-1")).isEqualTo(1);
        assertThat(signals.collect(app)).isEqualTo(signalsAfterFirst);
    }

    @Test
    void sameIdWithDifferentPayloadIsAConflict() {
        service.evaluate(TestFixtures.clean().id("IDEM-2").pan("IDEMP2222B").phone("9444444444")
                .device("dev-idem-2").submittedAt(Instant.now()).build());

        assertThatThrownBy(() -> service.evaluate(TestFixtures.clean().id("IDEM-2").pan("IDEMP2222B")
                .phone("9444444444").device("dev-idem-2").amount("777777").submittedAt(Instant.now()).build()))
                .isInstanceOf(ConflictException.class);
    }
}
