package com.azentio.doorstep.api.dto;

import com.azentio.doorstep.persistence.CaseStatus;

import java.time.Instant;

public record AuditEntry(CaseStatus fromStatus, CaseStatus toStatus, String actor, String comment, Instant at) {
}
