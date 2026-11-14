package com.azentio.doorstep.rules;

import com.azentio.doorstep.TestFixtures;
import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.azentio.doorstep.config.FraudProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FraudRulesTest {

    private final FraudProperties props = TestFixtures.defaultProperties();
    private final CrossApplicationSignals none = CrossApplicationSignals.none();

    private static CrossApplicationSignals identities(CrossApplicationSignals.Identity... ids) {
        return new CrossApplicationSignals(List.of(ids), 1, 1);
    }

    @Nested
    class PanReused {
        private final PanReusedDifferentIdentityRule rule = new PanReusedDifferentIdentityRule(props);
        private final ApplicationRequest app = TestFixtures.clean().build();

        @Test
        void firesWhenSamePanSeenWithDifferentName() {
            var signals = identities(new CrossApplicationSignals.Identity("APP-0000", "ASHA VERMAA", app.dob()));
            assertThat(rule.evaluate(app, signals)).hasValueSatisfying(hit -> {
                assertThat(hit.points()).isEqualTo(60);
                assertThat(hit.reason()).contains("******234F").doesNotContain("ABCDE1234F");
            });
        }

        @Test
        void firesWhenSamePanSeenWithDifferentDob() {
            var signals = identities(new CrossApplicationSignals.Identity("APP-0000", "ASHA VERMA", LocalDate.of(1991, 5, 17)));
            assertThat(rule.evaluate(app, signals)).isPresent();
        }

        @Test
        void doesNotFireWhenOtherApplicationHasSameIdentityAfterNormalising() {
            var signals = identities(new CrossApplicationSignals.Identity("APP-0000", Names.normalise("  asha   VERMA "), app.dob()));
            assertThat(rule.evaluate(app, signals)).isEmpty();
        }

        @Test
        void doesNotFireWhenPanNotSeenBefore() {
            assertThat(rule.evaluate(app, none)).isEmpty();
        }
    }

    @Nested
    class PhoneShared {
        private final PhoneSharedRule rule = new PhoneSharedRule(props);
        private final ApplicationRequest app = TestFixtures.clean().build();

        @Test
        void firesAtThreeDistinctPans() {
            assertThat(rule.evaluate(app, new CrossApplicationSignals(List.of(), 3, 1)))
                    .hasValueSatisfying(hit -> assertThat(hit.reason()).contains("******3210").doesNotContain("9876543210"));
        }

        @Test
        void doesNotFireAtTwoDistinctPans() {
            assertThat(rule.evaluate(app, new CrossApplicationSignals(List.of(), 2, 1))).isEmpty();
        }
    }

    @Nested
    class DeviceShared {
        private final DeviceSharedRule rule = new DeviceSharedRule(props);
        private final ApplicationRequest app = TestFixtures.clean().build();

        @Test
        void firesAtThreeDistinctPans() {
            assertThat(rule.evaluate(app, new CrossApplicationSignals(List.of(), 1, 3)))
                    .hasValueSatisfying(hit -> assertThat(hit.points()).isEqualTo(35));
        }

        @Test
        void doesNotFireAtTwoDistinctPans() {
            assertThat(rule.evaluate(app, new CrossApplicationSignals(List.of(), 1, 2))).isEmpty();
        }
    }

    @Nested
    class KycMismatch {
        private final KycMismatchRule rule = new KycMismatchRule(props);

        @Test
        void firesWhenNotVerified() {
            assertThat(rule.evaluate(TestFixtures.clean().verified(false).build(), none)).isPresent();
        }

        @Test
        void firesWhenNameOnIdDiffers() {
            assertThat(rule.evaluate(TestFixtures.clean().nameOnId("Asha Sharma").build(), none)).isPresent();
        }

        @Test
        void firesWhenDobOnIdDiffers() {
            assertThat(rule.evaluate(TestFixtures.clean().dobOnId(LocalDate.of(1990, 5, 18)).build(), none)).isPresent();
        }

        @Test
        void ignoresCaseAndWhitespaceDifferences() {
            assertThat(rule.evaluate(TestFixtures.clean().nameOnId("  ASHA    verma ").build(), none)).isEmpty();
        }
    }

    @Nested
    class ThinFile {
        private final ThinFileHighAmountRule rule = new ThinFileHighAmountRule(props);

        @Test
        void firesForNullScoreAndHighAmount() {
            assertThat(rule.evaluate(TestFixtures.clean().bureauScore(null).amount("300001").build(), none)).isPresent();
        }

        @Test
        void firesForShortHistoryAndHighAmount() {
            assertThat(rule.evaluate(TestFixtures.clean().historyMonths(5).amount("500000").build(), none)).isPresent();
        }

        @Test
        void doesNotFireAtExactlyTheAmountLimit() {
            assertThat(rule.evaluate(TestFixtures.clean().bureauScore(null).amount("300000").build(), none)).isEmpty();
        }

        @Test
        void doesNotFireForEstablishedFile() {
            assertThat(rule.evaluate(TestFixtures.clean().historyMonths(6).amount("900000").build(), none)).isEmpty();
        }
    }

    @Nested
    class IncomeInflated {
        private final IncomeInflatedRule rule = new IncomeInflatedRule(props);

        @Test
        void firesAboveThreeTimesBureauEstimate() {
            assertThat(rule.evaluate(TestFixtures.clean().bureauIncome("20000").declaredIncome("60001").build(), none)).isPresent();
        }

        @Test
        void doesNotFireAtExactlyThreeTimes() {
            assertThat(rule.evaluate(TestFixtures.clean().bureauIncome("20000").declaredIncome("60000").build(), none)).isEmpty();
        }

        @Test
        void doesNotFireWithoutBureauEstimate() {
            assertThat(rule.evaluate(TestFixtures.clean().bureauIncome(null).declaredIncome("999999").build(), none)).isEmpty();
        }
    }
}
