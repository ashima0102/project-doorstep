package com.azentio.doorstep.signals;

import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.azentio.doorstep.config.FraudProperties;
import com.azentio.doorstep.rules.CrossApplicationSignals;
import com.azentio.doorstep.rules.Names;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bridges the SignalStore and the rules: reads what earlier applications left behind (windows anchored
 * on the injected Clock), then records this application so later ones can see it.
 */
@Component
public class SignalCollector {

    private static final String SEP = "|";

    private final SignalStore store;
    private final Clock clock;
    private final Duration panWindow;
    private final Duration phoneWindow;
    private final Duration deviceWindow;

    public SignalCollector(SignalStore store, Clock clock, FraudProperties properties) {
        this.store = store;
        this.clock = clock;
        this.panWindow = properties.rules().panReused().window();
        this.phoneWindow = properties.rules().phoneShared().window();
        this.deviceWindow = properties.rules().deviceShared().window();
    }

    public CrossApplicationSignals collect(ApplicationRequest app) {
        Instant now = clock.instant();
        String panHash = Hashing.sha256(app.pan());

        List<CrossApplicationSignals.Identity> others = store
                .membersSince(SignalType.PAN_IDENTITY, app.pan(), now.minus(panWindow)).stream()
                .map(SignalCollector::parseIdentity)
                .filter(identity -> !identity.applicationId().equals(app.applicationId()))
                .toList();

        return new CrossApplicationSignals(others,
                distinctPansIncluding(SignalType.PHONE_PANS, app.phone(), panHash, now.minus(phoneWindow)),
                distinctPansIncluding(SignalType.DEVICE_PANS, app.deviceId(), panHash, now.minus(deviceWindow)));
    }

    public void record(ApplicationRequest app) {
        Instant at = app.submittedAt();
        String panHash = Hashing.sha256(app.pan());
        String identity = app.applicationId() + SEP + app.dob() + SEP + Names.normalise(app.applicantName());
        store.record(SignalType.PAN_IDENTITY, app.pan(), identity, at, panWindow);
        store.record(SignalType.PHONE_PANS, app.phone(), panHash, at, phoneWindow);
        store.record(SignalType.DEVICE_PANS, app.deviceId(), panHash, at, deviceWindow);
    }

    private int distinctPansIncluding(SignalType type, String key, String panHash, Instant since) {
        Set<String> pans = new HashSet<>(store.membersSince(type, key, since));
        pans.add(panHash);
        return pans.size();
    }

    private static CrossApplicationSignals.Identity parseIdentity(String member) {
        String[] parts = member.split("\\|", 3);
        return new CrossApplicationSignals.Identity(parts[0], parts[2], LocalDate.parse(parts[1]));
    }
}
