package com.azentio.doorstep.generator;

import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.azentio.doorstep.api.dto.EvaluationResponse;
import com.azentio.doorstep.rules.RuleHit;
import com.azentio.doorstep.service.ApplicationEvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the seeded generator through the real evaluation service and checks every seeded pattern is caught. */
@SpringBootTest
@ActiveProfiles("test")
class GeneratorPipelineTest {

    @Autowired
    ApplicationEvaluationService service;

    @Test
    void seededFraudPatternsProduceCases() {
        List<ApplicationRequest> applications =
                new SyntheticApplicationGenerator(42L, Instant.now().minus(Duration.ofHours(1)), "PIPE").generate(500);
        assertThat(applications).hasSizeGreaterThanOrEqualTo(500);

        Map<String, Integer> decisions = new TreeMap<>();
        Map<String, Integer> rules = new TreeMap<>();
        int cases = 0;
        for (ApplicationRequest application : applications) {
            EvaluationResponse result = service.evaluate(application);
            decisions.merge(result.decision().name(), 1, Integer::sum);
            result.ruleHits().stream().map(RuleHit::ruleId).forEach(id -> rules.merge(id, 1, Integer::sum));
            if (result.caseId() != null) {
                cases++;
            }
        }
        System.out.printf("Generator run: decisions=%s rules=%s cases=%d%n", decisions, rules, cases);

        assertThat(rules).containsKeys("PHONE_SHARED", "DEVICE_SHARED", "PAN_REUSED_DIFFERENT_IDENTITY",
                "INCOME_INFLATED", "KYC_MISMATCH", "THIN_FILE_HIGH_AMOUNT");
        assertThat(rules.get("PHONE_SHARED")).isEqualTo(6);              // 3rd..8th PAN on the ring phone
        assertThat(rules.get("DEVICE_SHARED")).isEqualTo(6);             // 3rd..8th PAN on the ring device
        assertThat(rules.get("PAN_REUSED_DIFFERENT_IDENTITY")).isEqualTo(4);
        assertThat(cases).isEqualTo(decisions.getOrDefault("REVIEW", 0) + decisions.getOrDefault("REJECT", 0));
        assertThat(decisions.get("PASS")).isGreaterThan(400);
    }
}
