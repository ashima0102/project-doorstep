package com.azentio.doorstep.service;

import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.azentio.doorstep.api.dto.EvaluationResponse;
import com.azentio.doorstep.masking.Masking;
import com.azentio.doorstep.persistence.FraudCase;
import com.azentio.doorstep.persistence.FraudCaseRepository;
import com.azentio.doorstep.persistence.LoanApplication;
import com.azentio.doorstep.persistence.LoanApplicationRepository;
import com.azentio.doorstep.persistence.RuleHitRecord;
import com.azentio.doorstep.persistence.RuleHitRepository;
import com.azentio.doorstep.rules.FraudEvaluator;
import com.azentio.doorstep.rules.RuleHit;
import com.azentio.doorstep.signals.SignalCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** The single evaluation path, used by both the REST controller and the Kafka consumer. */
@Service
public class ApplicationEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEvaluationService.class);

    private final SignalCollector signals;
    private final FraudEvaluator evaluator;
    private final LoanApplicationRepository applications;
    private final RuleHitRepository ruleHits;
    private final FraudCaseRepository cases;
    private final Clock clock;

    public ApplicationEvaluationService(SignalCollector signals, FraudEvaluator evaluator,
                                        LoanApplicationRepository applications, RuleHitRepository ruleHits,
                                        FraudCaseRepository cases, Clock clock) {
        this.signals = signals;
        this.evaluator = evaluator;
        this.applications = applications;
        this.ruleHits = ruleHits;
        this.cases = cases;
        this.clock = clock;
    }

    @Transactional
    public EvaluationResponse evaluate(ApplicationRequest request) {
        if (applications.existsById(request.applicationId())) {
            throw new ConflictException("Application " + request.applicationId() + " has already been evaluated");
        }
        FraudEvaluator.Evaluation result = evaluator.evaluate(request, signals.collect(request));
        signals.record(request);

        Instant now = clock.instant();
        applications.save(new LoanApplication(request, result.decision(), result.score(), now));
        result.hits().forEach(hit -> ruleHits.save(new RuleHitRecord(request.applicationId(), hit)));

        UUID caseId = null;
        if (result.decision().opensCase()) {
            caseId = cases.save(FraudCase.open(request.applicationId(), result.score(), result.decision(), now)).getCaseId();
        }
        log.info("Evaluated application={} pan={} phone={} decision={} score={} rules={}",
                request.applicationId(), Masking.pan(request.pan()), Masking.phone(request.phone()),
                result.decision(), result.score(), result.hits().stream().map(RuleHit::ruleId).toList());
        return new EvaluationResponse(request.applicationId(), result.decision(), result.score(), result.hits(), caseId);
    }
}
