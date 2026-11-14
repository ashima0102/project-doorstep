package com.azentio.doorstep.rules;

import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.azentio.doorstep.config.FraudProperties;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DeviceSharedRule implements FraudRule {

    public static final String ID = "DEVICE_SHARED";
    private final FraudProperties.SharedIdentifier config;

    public DeviceSharedRule(FraudProperties properties) {
        this.config = properties.rules().deviceShared();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<RuleHit> evaluate(ApplicationRequest app, CrossApplicationSignals signals) {
        int pans = signals.distinctPansOnDevice();
        if (pans < config.minDistinctPans()) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit(ID, config.points(),
                "Device %s is linked to %d distinct PANs in the last %d hours"
                        .formatted(app.deviceId(), pans, config.window().toHours())));
    }
}
