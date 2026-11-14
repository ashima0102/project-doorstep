package com.azentio.doorstep.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Immutable once written: no setters, and the repository only exposes save and find. */
@Entity
@Table(name = "case_audit")
public class CaseAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID caseId;
    @Enumerated(EnumType.STRING)
    private CaseStatus fromStatus;
    @Enumerated(EnumType.STRING)
    private CaseStatus toStatus;
    private String actor;
    private String comment;
    @Column(name = "at")
    private Instant at;

    protected CaseAudit() {
    }

    public CaseAudit(UUID caseId, CaseStatus fromStatus, CaseStatus toStatus, String actor, String comment, Instant at) {
        this.caseId = caseId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.comment = comment;
        this.at = at;
    }

    public UUID getCaseId() { return caseId; }
    public CaseStatus getFromStatus() { return fromStatus; }
    public CaseStatus getToStatus() { return toStatus; }
    public String getActor() { return actor; }
    public String getComment() { return comment; }
    public Instant getAt() { return at; }
}
