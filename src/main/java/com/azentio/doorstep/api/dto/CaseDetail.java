package com.azentio.doorstep.api.dto;

import com.azentio.doorstep.persistence.CaseStatus;
import com.azentio.doorstep.rules.Decision;
import com.azentio.doorstep.rules.RuleHit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CaseDetail(UUID caseId,
                         String applicationId,
                         String applicantName,
                         String maskedPan,
                         String maskedPhone,
                         String deviceId,
                         BigDecimal requestedAmount,
                         int score,
                         Decision decision,
                         CaseStatus status,
                         Instant createdAt,
                         Instant updatedAt,
                         List<RuleHit> ruleHits) {
}
