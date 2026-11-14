package com.azentio.doorstep.api.dto;

import com.azentio.doorstep.persistence.CaseStatus;
import com.azentio.doorstep.rules.Decision;

import java.time.Instant;
import java.util.UUID;

public record CaseSummary(UUID caseId,
                          String applicationId,
                          String applicantName,
                          String maskedPan,
                          String maskedPhone,
                          int score,
                          Decision decision,
                          CaseStatus status,
                          Instant createdAt) {
}
