package com.azentio.doorstep.rules;

import com.azentio.doorstep.TestFixtures;
import com.azentio.doorstep.api.dto.ApplicationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Same application, same code: only the fraud.thresholds.review property differs.
 * With the yml default (30) an inflated-income-only application (20 points) passes;
 * lowering the threshold to 20 sends it to REVIEW.
 */
@SpringBootTest(properties = "fraud.thresholds.review=20")
@ActiveProfiles("test")
class ThresholdConfigTest {

    @Autowired
    FraudEvaluator configuredEvaluator;

    private final ApplicationRequest inflatedIncomeOnly = TestFixtures.clean().bureauIncome("20000").declaredIncome("80000").build();

    @Test
    void defaultThresholdPasses() {
        var defaults = new FraudEvaluator(List.of(new IncomeInflatedRule(TestFixtures.defaultProperties())),
                TestFixtures.defaultProperties());
        assertThat(defaults.evaluate(inflatedIncomeOnly, CrossApplicationSignals.none()).decision())
                .isEqualTo(Decision.PASS);
    }

    @Test
    void loweredThresholdFromConfigurationSendsToReview() {
        var result = configuredEvaluator.evaluate(inflatedIncomeOnly, CrossApplicationSignals.none());
        assertThat(result.score()).isEqualTo(20);
        assertThat(result.decision()).isEqualTo(Decision.REVIEW);
    }
}
