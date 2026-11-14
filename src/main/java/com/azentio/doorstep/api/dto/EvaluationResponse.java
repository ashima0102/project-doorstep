package com.azentio.doorstep.api.dto;

import com.azentio.doorstep.rules.Decision;
import com.azentio.doorstep.rules.RuleHit;

import java.util.List;
import java.util.UUID;

/** caseId is null for PASS decisions. */
public record EvaluationResponse(String applicationId,
                                 Decision decision,
                                 int score,
                                 List<RuleHit> ruleHits,
                                 UUID caseId) {
}
