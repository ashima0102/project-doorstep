package com.azentio.doorstep.service;

import com.azentio.doorstep.rules.Decision;

import java.util.List;

/** Published once per newly evaluated application; carries the masked PAN only. */
public record DecisionMadeEvent(String applicationId, Decision decision, int score, List<String> ruleIds,
                                String maskedPan) {
}
