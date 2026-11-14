package com.azentio.doorstep.rules;

import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.azentio.doorstep.config.FraudProperties;
import com.azentio.doorstep.masking.Masking;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PanReusedDifferentIdentityRule implements FraudRule {

    public static final String ID = "PAN_REUSED_DIFFERENT_IDENTITY";
    private final FraudProperties.PanReused config;

    public PanReusedDifferentIdentityRule(FraudProperties properties) {
        this.config = properties.rules().panReused();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<RuleHit> evaluate(ApplicationRequest app, CrossApplicationSignals signals) {
        String name = Names.normalise(app.applicantName());
        return signals.otherIdentitiesOnPan().stream()
                .filter(other -> !other.normalisedName().equals(name) || !other.dob().equals(app.dob()))
                .findFirst()
                .map(other -> new RuleHit(ID, config.points(),
                        "PAN %s was used on application %s within %d days with a different name or date of birth"
                                .formatted(Masking.pan(app.pan()), other.applicationId(), config.window().toDays())));
    }
}
