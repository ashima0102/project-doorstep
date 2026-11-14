package com.azentio.doorstep.rules;

import java.time.LocalDate;
import java.util.List;

/**
 * What other applications inside each configured window tell us about this one.
 * Collected from the SignalStore before rules run, so the rules themselves stay pure functions.
 *
 * @param otherIdentitiesOnPan identities (normalised name + DOB) seen on the same PAN in other applications
 * @param distinctPansOnPhone  distinct PANs linked to the phone, counting this application
 * @param distinctPansOnDevice distinct PANs linked to the device, counting this application
 */
public record CrossApplicationSignals(List<Identity> otherIdentitiesOnPan,
                                      int distinctPansOnPhone,
                                      int distinctPansOnDevice) {

    public record Identity(String applicationId, String normalisedName, LocalDate dob) {
    }

    public static CrossApplicationSignals none() {
        return new CrossApplicationSignals(List.of(), 1, 1);
    }
}
