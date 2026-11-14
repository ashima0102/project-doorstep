package com.azentio.doorstep.ingest;

import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.azentio.doorstep.service.ApplicationEvaluationService;
import com.azentio.doorstep.service.EvaluationOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/** Consumes partner applications and runs them through the same service as POST /api/applications. */
@Component
@ConditionalOnProperty(name = "doorstep.kafka.enabled", havingValue = "true")
public class ApplicationEventListener {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEventListener.class);

    private final ApplicationEvaluationService service;
    private final ObjectMapper json;
    private final Validator validator;
    private final Timer latency;
    private final Counter evaluated;
    private final Counter duplicates;

    public ApplicationEventListener(ApplicationEvaluationService service, ObjectMapper json, Validator validator,
                                    MeterRegistry meters) {
        this.service = service;
        this.json = json;
        this.validator = validator;
        this.latency = Timer.builder("doorstep.evaluation.latency")
                .description("Consume-to-decision latency for Kafka applications")
                .publishPercentiles(0.95)
                .register(meters);
        this.evaluated = meters.counter("doorstep.ingest.evaluated");
        this.duplicates = meters.counter("doorstep.ingest.duplicates");
    }

    @KafkaListener(topics = "${doorstep.kafka.applications-topic}")
    public void onApplication(ConsumerRecord<String, String> record) {
        Timer.Sample sample = Timer.start();
        ApplicationRequest request = parse(record);
        EvaluationOutcome outcome = service.evaluate(request);
        if (outcome.duplicate()) {
            duplicates.increment();
            log.info("Skipped exact redelivery of application={}", request.applicationId());
            return;
        }
        sample.stop(latency);
        evaluated.increment();
    }

    private ApplicationRequest parse(ConsumerRecord<String, String> record) {
        ApplicationRequest request;
        try {
            request = json.readValue(record.value(), ApplicationRequest.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new MalformedEventException("Unparseable application event with key " + record.key(), e);
        }
        Set<ConstraintViolation<ApplicationRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new MalformedEventException("Invalid application event: " + violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining(", ")));
        }
        if (record.key() != null && !record.key().equals(request.applicationId())) {
            throw new MalformedEventException("Message key does not match applicationId " + request.applicationId());
        }
        return request;
    }
}
