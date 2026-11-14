package com.azentio.doorstep.rules;

import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.azentio.doorstep.config.FraudProperties;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ThinFileHighAmountRule implements FraudRule {

    public static final String ID = "THIN_FILE_HIGH_AMOUNT";
    private final FraudProperties.ThinFile config;

    public ThinFileHighAmountRule(FraudProperties properties) {
        this.config = properties.rules().thinFile();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<RuleHit> evaluate(ApplicationRequest app, CrossApplicationSignals signals) {
        ApplicationRequest.Bureau bureau = app.bureau();
        boolean thinFile = bureau.score() == null || bureau.historyMonths() < config.minHistoryMonths();
        boolean highAmount = app.requestedAmount().compareTo(config.amountAbove()) > 0;
        if (!thinFile || !highAmount) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit(ID, config.points(),
                "Thin bureau file (score %s, %d months history) with requested amount %s above %s"
                        .formatted(bureau.score() == null ? "none" : bureau.score(), bureau.historyMonths(),
                                app.requestedAmount().toPlainString(), config.amountAbove().toPlainString())));
    }
}
