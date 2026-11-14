package com.azentio.doorstep.rules;

import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.azentio.doorstep.config.FraudProperties;
import com.azentio.doorstep.masking.Masking;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PhoneSharedRule implements FraudRule {

    public static final String ID = "PHONE_SHARED";
    private final FraudProperties.SharedIdentifier config;

    public PhoneSharedRule(FraudProperties properties) {
        this.config = properties.rules().phoneShared();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<RuleHit> evaluate(ApplicationRequest app, CrossApplicationSignals signals) {
        int pans = signals.distinctPansOnPhone();
        if (pans < config.minDistinctPans()) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit(ID, config.points(),
                "Phone %s is linked to %d distinct PANs in the last %d days"
                        .formatted(Masking.phone(app.phone()), pans, config.window().toDays())));
    }
}
