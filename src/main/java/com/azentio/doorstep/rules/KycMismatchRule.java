package com.azentio.doorstep.rules;

import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.azentio.doorstep.config.FraudProperties;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class KycMismatchRule implements FraudRule {

    public static final String ID = "KYC_MISMATCH";
    private final int points;

    public KycMismatchRule(FraudProperties properties) {
        this.points = properties.rules().kycMismatch().points();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<RuleHit> evaluate(ApplicationRequest app, CrossApplicationSignals signals) {
        ApplicationRequest.Kyc kyc = app.kyc();
        String reason;
        if (!Boolean.TRUE.equals(kyc.verified())) {
            reason = "KYC is not verified";
        } else if (!Names.normalise(kyc.nameOnId()).equals(Names.normalise(app.applicantName()))) {
            reason = "Name on ID does not match the application";
        } else if (!kyc.dobOnId().equals(app.dob())) {
            reason = "Date of birth on ID does not match the application";
        } else {
            return Optional.empty();
        }
        return Optional.of(new RuleHit(ID, points, reason));
    }
}
