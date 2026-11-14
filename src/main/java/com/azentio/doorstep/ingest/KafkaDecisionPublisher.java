package com.azentio.doorstep.ingest;

import com.azentio.doorstep.service.DecisionMadeEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Publishes each decision to loan.decisions.v1 after the evaluation transaction commits. */
@Component
@ConditionalOnProperty(name = "doorstep.kafka.enabled", havingValue = "true")
public class KafkaDecisionPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final String topic;

    public KafkaDecisionPublisher(KafkaTemplate<String, String> kafka, ObjectMapper json,
                                  @Value("${doorstep.kafka.decisions-topic}") String topic) {
        this.kafka = kafka;
        this.json = json;
        this.topic = topic;
    }

    @TransactionalEventListener
    public void onDecision(DecisionMadeEvent event) throws JsonProcessingException {
        kafka.send(topic, event.applicationId(), json.writeValueAsString(event));
    }
}
