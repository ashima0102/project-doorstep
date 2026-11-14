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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * The single evaluation path, used by both the REST controller and the Kafka consumer.
 * Idempotent per applicationId: an exact repeat returns the stored result without writing anything;
 * the same id with a different payload is a conflict.
 */
@Service
public class ApplicationEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEvaluationService.class);

    private final SignalCollector signals;
    private final FraudEvaluator evaluator;
    private final LoanApplicationRepository applications;
    private final RuleHitRepository ruleHits;
    private final FraudCaseRepository cases;
    private final PayloadHasher hasher;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Counter casesCreated;

    public ApplicationEvaluationService(SignalCollector signals, FraudEvaluator evaluator,
                                        LoanApplicationRepository applications, RuleHitRepository ruleHits,
                                        FraudCaseRepository cases, PayloadHasher hasher,
                                        ApplicationEventPublisher events, Clock clock, MeterRegistry meters) {
        this.signals = signals;
        this.evaluator = evaluator;
        this.applications = applications;
        this.ruleHits = ruleHits;
        this.cases = cases;
        this.hasher = hasher;
        this.events = events;
        this.clock = clock;
        this.casesCreated = meters.counter("doorstep.cases.created");
    }

    @Transactional
    public EvaluationOutcome evaluate(ApplicationRequest request) {
        String payloadHash = hasher.hash(request);
        var existing = applications.findById(request.applicationId());
        if (existing.isPresent()) {
            if (!payloadHash.equals(existing.get().getPayloadHash())) {
                throw new ConflictException("Application " + request.applicationId()
                        + " was already received with a different payload");
            }
            return new EvaluationOutcome(storedResult(existing.get()), true);
        }

        FraudEvaluator.Evaluation result = evaluator.evaluate(request, signals.collect(request));
        Instant now = clock.instant();
        // Insert first: the primary key rejects a concurrent duplicate before Redis is touched.
        applications.saveAndFlush(new LoanApplication(request, payloadHash, result.decision(), result.score(), now));
        signals.record(request);
        result.hits().forEach(hit -> ruleHits.save(new RuleHitRecord(request.applicationId(), hit)));

        UUID caseId = null;
        if (result.decision().opensCase()) {
            caseId = cases.save(FraudCase.open(request.applicationId(), result.score(), result.decision(), now)).getCaseId();
            casesCreated.increment();
        }
        var ruleIds = result.hits().stream().map(RuleHit::ruleId).toList();
        events.publishEvent(new DecisionMadeEvent(request.applicationId(), result.decision(), result.score(),
                ruleIds, Masking.pan(request.pan())));
        log.info("Evaluated application={} pan={} phone={} decision={} score={} rules={}",
                request.applicationId(), Masking.pan(request.pan()), Masking.phone(request.phone()),
                result.decision(), result.score(), ruleIds);
        return new EvaluationOutcome(
                new EvaluationResponse(request.applicationId(), result.decision(), result.score(), result.hits(), caseId),
                false);
    }

    private EvaluationResponse storedResult(LoanApplication app) {
        UUID caseId = cases.findByApplicationId(app.getApplicationId()).map(FraudCase::getCaseId).orElse(null);
        return new EvaluationResponse(app.getApplicationId(), app.getDecision(), app.getScore(),
                ruleHits.findByApplicationIdOrderByPointsDesc(app.getApplicationId()).stream()
                        .map(RuleHitRecord::toRuleHit).toList(),
                caseId);
    }
}
