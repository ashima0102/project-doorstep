package com.azentio.doorstep.rules;

/** A rule that fired. The reason is shown to underwriters, so it must never contain a full PAN or phone. */
public record RuleHit(String ruleId, int points, String reason) {
}
