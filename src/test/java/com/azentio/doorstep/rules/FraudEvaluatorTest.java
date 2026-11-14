package com.azentio.doorstep.rules;

import com.azentio.doorstep.TestFixtures;
import com.azentio.doorstep.api.dto.ApplicationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FraudEvaluatorTest {

    /** A rule that always fires with a fixed weight, to pin the score exactly on a boundary. */
    private static FraudRule fixed(int points) {
        return new FraudRule() {
            public String id() {
                return "FIXED_" + points;
            }

            public Optional<RuleHit> evaluate(ApplicationRequest a, CrossApplicationSignals s) {
                return Optional.of(new RuleHit(id(), points, "fixed"));
            }
        };
    }

    private Decision decisionFor(int score) {
        var evaluator = new FraudEvaluator(List.of(fixed(score)), TestFixtures.defaultProperties());
        return evaluator.evaluate(TestFixtures.clean().build(), CrossApplicationSignals.none()).decision();
    }

    @ParameterizedTest(name = "score {0} -> {1}")
    @CsvSource({"0,PASS", "29,PASS", "30,REVIEW", "59,REVIEW", "60,REJECT", "130,REJECT"})
    void mapsScoreToDecisionAtBoundaries(int score, Decision expected) {
        assertThat(decisionFor(score)).isEqualTo(expected);
    }

    @Test
    void scoreIsSumOfFiredRules() {
        var evaluator = new FraudEvaluator(List.of(fixed(15), fixed(20)), TestFixtures.defaultProperties());
        var result = evaluator.evaluate(TestFixtures.clean().build(), CrossApplicationSignals.none());
        assertThat(result.score()).isEqualTo(35);
        assertThat(result.decision()).isEqualTo(Decision.REVIEW);
        assertThat(result.hits()).hasSize(2);
    }
}
