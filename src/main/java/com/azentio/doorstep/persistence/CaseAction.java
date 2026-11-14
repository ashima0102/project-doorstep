package com.azentio.doorstep.persistence;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Underwriter actions and the server-side state machine they drive. */
public enum CaseAction {
    APPROVE(CaseStatus.APPROVED),
    REJECT(CaseStatus.REJECTED),
    ESCALATE(CaseStatus.ESCALATED);

    private static final Map<CaseStatus, Set<CaseStatus>> ALLOWED = Map.of(
            CaseStatus.OPEN, Set.of(CaseStatus.APPROVED, CaseStatus.REJECTED, CaseStatus.ESCALATED),
            CaseStatus.ESCALATED, Set.of(CaseStatus.APPROVED, CaseStatus.REJECTED));

    private final CaseStatus target;

    CaseAction(CaseStatus target) {
        this.target = target;
    }

    /** Empty when the transition is not allowed (APPROVED and REJECTED are final). */
    public Optional<CaseStatus> transitionFrom(CaseStatus current) {
        return ALLOWED.getOrDefault(current, Set.of()).contains(target) ? Optional.of(target) : Optional.empty();
    }
}
