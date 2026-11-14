package com.azentio.doorstep.rules;

import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.azentio.doorstep.config.FraudProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class IncomeInflatedRule implements FraudRule {

    public static final String ID = "INCOME_INFLATED";
    private final FraudProperties.IncomeInflated config;

    public IncomeInflatedRule(FraudProperties properties) {
        this.config = properties.rules().incomeInflated();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<RuleHit> evaluate(ApplicationRequest app, CrossApplicationSignals signals) {
        BigDecimal estimate = app.bureau().estimatedMonthlyIncome();
        if (estimate == null || estimate.signum() <= 0) {
            return Optional.empty(); // nothing to compare against; thin files are handled by THIN_FILE_HIGH_AMOUNT
        }
        BigDecimal limit = estimate.multiply(config.multiplier());
        if (app.declaredMonthlyIncome().compareTo(limit) <= 0) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit(ID, config.points(),
                "Declared monthly income %s is more than %sx the bureau estimate %s"
                        .formatted(app.declaredMonthlyIncome().toPlainString(),
                                config.multiplier().toPlainString(), estimate.toPlainString())));
    }
}
