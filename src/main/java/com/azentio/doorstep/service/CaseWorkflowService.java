package com.azentio.doorstep.service;

import com.azentio.doorstep.api.dto.AuditEntry;
import com.azentio.doorstep.persistence.CaseAction;
import com.azentio.doorstep.persistence.CaseAudit;
import com.azentio.doorstep.persistence.CaseAuditRepository;
import com.azentio.doorstep.persistence.CaseStatus;
import com.azentio.doorstep.persistence.FraudCase;
import com.azentio.doorstep.persistence.FraudCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CaseWorkflowService {

    private final FraudCaseRepository cases;
    private final CaseAuditRepository audit;
    private final Clock clock;

    public CaseWorkflowService(FraudCaseRepository cases, CaseAuditRepository audit, Clock clock) {
        this.cases = cases;
        this.audit = audit;
        this.clock = clock;
    }

    /** Applies the action if the state machine allows it and appends one audit row in the same transaction. */
    @Transactional
    public void act(UUID caseId, CaseAction action, String actor, String comment) {
        FraudCase fraudCase = cases.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Case " + caseId + " not found"));
        CaseStatus from = fraudCase.getStatus();
        CaseStatus to = action.transitionFrom(from).orElseThrow(() ->
                new ConflictException("Cannot " + action + " a case that is " + from));
        Instant now = clock.instant();
        fraudCase.moveTo(to, now);
        audit.save(new CaseAudit(caseId, from, to, actor, comment.trim(), now));
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> history(UUID caseId) {
        if (!cases.existsById(caseId)) {
            throw new NotFoundException("Case " + caseId + " not found");
        }
        return audit.findByCaseIdOrderByAtAsc(caseId).stream()
                .map(a -> new AuditEntry(a.getFromStatus(), a.getToStatus(), a.getActor(), a.getComment(), a.getAt()))
                .toList();
    }
}
