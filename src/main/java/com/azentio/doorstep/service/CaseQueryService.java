package com.azentio.doorstep.service;

import com.azentio.doorstep.api.dto.CaseDetail;
import com.azentio.doorstep.api.dto.CaseSummary;
import com.azentio.doorstep.masking.Masking;
import com.azentio.doorstep.persistence.CaseStatus;
import com.azentio.doorstep.persistence.FraudCase;
import com.azentio.doorstep.persistence.FraudCaseRepository;
import com.azentio.doorstep.persistence.LoanApplication;
import com.azentio.doorstep.persistence.LoanApplicationRepository;
import com.azentio.doorstep.persistence.RuleHitRecord;
import com.azentio.doorstep.persistence.RuleHitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read side of the case queue. Everything leaving here carries masked PAN and phone only. */
@Service
@Transactional(readOnly = true)
public class CaseQueryService {

    private final FraudCaseRepository cases;
    private final LoanApplicationRepository applications;
    private final RuleHitRepository ruleHits;

    public CaseQueryService(FraudCaseRepository cases, LoanApplicationRepository applications,
                            RuleHitRepository ruleHits) {
        this.cases = cases;
        this.applications = applications;
        this.ruleHits = ruleHits;
    }

    public List<CaseSummary> list(CaseStatus status) {
        List<FraudCase> found = status == null
                ? cases.findAllByOrderByScoreDescCreatedAtAsc()
                : cases.findByStatusOrderByScoreDescCreatedAtAsc(status);
        Map<String, LoanApplication> apps = applications
                .findAllById(found.stream().map(FraudCase::getApplicationId).toList()).stream()
                .collect(Collectors.toMap(LoanApplication::getApplicationId, Function.identity()));
        return found.stream().map(c -> summary(c, apps.get(c.getApplicationId()))).toList();
    }

    public CaseDetail get(UUID caseId) {
        FraudCase c = cases.findById(caseId).orElseThrow(() -> new NotFoundException("Case " + caseId + " not found"));
        LoanApplication app = applications.findById(c.getApplicationId()).orElseThrow();
        return new CaseDetail(c.getCaseId(), app.getApplicationId(), app.getApplicantName(),
                Masking.pan(app.getPan()), Masking.phone(app.getPhone()), app.getDeviceId(), app.getRequestedAmount(),
                c.getScore(), c.getDecision(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt(),
                ruleHits.findByApplicationIdOrderByPointsDesc(app.getApplicationId()).stream()
                        .map(RuleHitRecord::toRuleHit).toList());
    }

    private static CaseSummary summary(FraudCase c, LoanApplication app) {
        return new CaseSummary(c.getCaseId(), c.getApplicationId(), app.getApplicantName(),
                Masking.pan(app.getPan()), Masking.phone(app.getPhone()),
                c.getScore(), c.getDecision(), c.getStatus(), c.getCreatedAt());
    }
}
