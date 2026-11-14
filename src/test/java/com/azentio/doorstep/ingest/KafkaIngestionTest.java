package com.azentio.doorstep.ingest;

import com.azentio.doorstep.TestFixtures;
import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.azentio.doorstep.persistence.FraudCaseRepository;
import com.azentio.doorstep.persistence.LoanApplicationRepository;
import com.azentio.doorstep.persistence.RuleHitRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs offline: @EmbeddedKafka starts an in-JVM broker. */
@SpringBootTest(properties = {
        "doorstep.kafka.enabled=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"})
@EmbeddedKafka(partitions = 1, topics = {"loan.applications.v1", "loan.applications.v1.DLT", "loan.decisions.v1"})
@ActiveProfiles("test")
class KafkaIngestionTest {

    private static final String TOPIC = "loan.applications.v1";

    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    EmbeddedKafkaBroker broker;
    @Autowired
    ObjectMapper json;
    @Autowired
    LoanApplicationRepository applications;
    @Autowired
    RuleHitRepository ruleHits;
    @Autowired
    FraudCaseRepository cases;

    @Test
    void sameApplicationTwiceGivesOneCaseOneSetOfRuleHitsAndOneDecisionEvent() throws Exception {
        // KYC not verified (30) + inflated income (20) = 50 -> REVIEW, 2 rule hits
        ApplicationRequest app = TestFixtures.clean().id("KAFKA-DUP-1").pan("KAFKA1234D").phone("9111111111")
                .device("dev-kafka-dup").verified(false).bureauIncome("10000").declaredIncome("90000")
                .submittedAt(Instant.now()).build();
        String payload = json.writeValueAsString(app);

        try (Consumer<String, String> decisions = consumer("decisions-check", "loan.decisions.v1")) {
            kafka.send(TOPIC, app.applicationId(), payload).get();
            kafka.send(TOPIC, app.applicationId(), payload).get();

            waitUntil(() -> cases.findByApplicationId(app.applicationId()).isPresent());
            List<ConsumerRecord<String, String>> events = drain(decisions, app.applicationId(), Duration.ofSeconds(5));

            assertThat(applications.count()).isPositive();
            assertThat(cases.findAll().stream().filter(c -> c.getApplicationId().equals(app.applicationId()))).hasSize(1);
            assertThat(ruleHits.countByApplicationId(app.applicationId())).isEqualTo(2);
            assertThat(events).hasSize(1);
            assertThat(events.get(0).value()).contains("******234D").doesNotContain("KAFKA1234D");
        }
    }

    @Test
    void malformedMessageAndConflictingRedeliveryLandOnDeadLetterTopic() throws Exception {
        ApplicationRequest app = TestFixtures.clean().id("KAFKA-CONFLICT-1").pan("CONFL5678C").phone("9222222222")
                .device("dev-kafka-conflict").submittedAt(Instant.now()).build();
        ApplicationRequest conflicting = TestFixtures.clean().id("KAFKA-CONFLICT-1").pan("CONFL5678C")
                .phone("9222222222").device("dev-kafka-conflict").amount("999999").submittedAt(app.submittedAt()).build();

        try (Consumer<String, String> dlt = consumer("dlt-check", "loan.applications.v1.DLT")) {
            kafka.send(TOPIC, "KAFKA-MALFORMED-1", "{this is not json").get();
            kafka.send(TOPIC, app.applicationId(), json.writeValueAsString(app)).get();
            waitUntil(() -> applications.existsById(app.applicationId()));
            kafka.send(TOPIC, conflicting.applicationId(), json.writeValueAsString(conflicting)).get();

            List<String> keys = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 15_000;
            while (keys.size() < 2 && System.currentTimeMillis() < deadline) {
                KafkaTestUtils.getRecords(dlt, Duration.ofMillis(500)).forEach(r -> keys.add(r.key()));
            }
            assertThat(keys).containsExactlyInAnyOrder("KAFKA-MALFORMED-1", "KAFKA-CONFLICT-1");
        }
    }

    private Consumer<String, String> consumer(String group, String topic) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(group, "true", broker);
        props.put("auto.offset.reset", "earliest");
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, topic);
        return consumer;
    }

    private static List<ConsumerRecord<String, String>> drain(Consumer<String, String> consumer, String key,
                                                              Duration window) {
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        long deadline = System.currentTimeMillis() + window.toMillis();
        while (System.currentTimeMillis() < deadline) {
            KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500)).forEach(r -> {
                if (key.equals(r.key())) {
                    matching.add(r);
                }
            });
        }
        return matching;
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Condition not met within 15s");
            }
            Thread.sleep(100);
        }
    }
}
