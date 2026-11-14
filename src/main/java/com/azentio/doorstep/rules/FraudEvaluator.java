package com.azentio.doorstep.rules;

import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.azentio.doorstep.config.FraudProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Runs every rule, sums the points and maps the score to a decision using the configured thresholds. */
@Component
public class FraudEvaluator {

    private final List<FraudRule> rules;
    private final FraudProperties.Thresholds thresholds;

    public FraudEvaluator(List<FraudRule> rules, FraudProperties properties) {
        this.rules = List.copyOf(rules);
        this.thresholds = properties.thresholds();
    }

    public Evaluation evaluate(ApplicationRequest application, CrossApplicationSignals signals) {
        List<RuleHit> hits = rules.stream()
                .map(rule -> rule.evaluate(application, signals))
                .flatMap(Optional::stream)
                .toList();
        int score = hits.stream().mapToInt(RuleHit::points).sum();
        return new Evaluation(decide(score), score, hits);
    }

    Decision decide(int score) {
        if (score >= thresholds.reject()) {
            return Decision.REJECT;
        }
        return score >= thresholds.review() ? Decision.REVIEW : Decision.PASS;
    }

    public record Evaluation(Decision decision, int score, List<RuleHit> hits) {
    }
}
