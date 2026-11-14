package com.azentio.doorstep.persistence;

import com.azentio.doorstep.rules.Decision;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_case")
public class FraudCase {

    @Id
    private UUID caseId;
    private String applicationId;
    private int score;
    @Enumerated(EnumType.STRING)
    private Decision decision;
    @Enumerated(EnumType.STRING)
    private CaseStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    protected FraudCase() {
    }

    public static FraudCase open(String applicationId, int score, Decision decision, Instant now) {
        FraudCase c = new FraudCase();
        c.caseId = UUID.randomUUID();
        c.applicationId = applicationId;
        c.score = score;
        c.decision = decision;
        c.status = CaseStatus.OPEN;
        c.createdAt = now;
        c.updatedAt = now;
        return c;
    }

    public void moveTo(CaseStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
    }

    public UUID getCaseId() { return caseId; }
    public String getApplicationId() { return applicationId; }
    public int getScore() { return score; }
    public Decision getDecision() { return decision; }
    public CaseStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
