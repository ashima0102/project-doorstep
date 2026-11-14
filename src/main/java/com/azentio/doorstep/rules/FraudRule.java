package com.azentio.doorstep.rules;

import com.azentio.doorstep.api.dto.ApplicationRequest;

import java.util.Optional;

public interface FraudRule {

    String id();

    Optional<RuleHit> evaluate(ApplicationRequest application, CrossApplicationSignals signals);
}
