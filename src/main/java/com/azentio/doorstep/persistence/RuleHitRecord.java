package com.azentio.doorstep.persistence;

import com.azentio.doorstep.rules.RuleHit;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rule_hit")
public class RuleHitRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String applicationId;
    private String ruleId;
    private int points;
    private String reason;

    protected RuleHitRecord() {
    }

    public RuleHitRecord(String applicationId, RuleHit hit) {
        this.applicationId = applicationId;
        this.ruleId = hit.ruleId();
        this.points = hit.points();
        this.reason = hit.reason();
    }

    public RuleHit toRuleHit() {
        return new RuleHit(ruleId, points, reason);
    }

    public String getApplicationId() { return applicationId; }
}
