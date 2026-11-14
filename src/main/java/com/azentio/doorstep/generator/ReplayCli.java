package com.azentio.doorstep.generator;

import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Publishes 1,000 new applications + 50 exact redeliveries + 10 conflicting redeliveries + 10 malformed
 * messages to loan.applications.v1, waits for the running service to drain them, then prints the
 * service's own counters. Run against a freshly started service on a clean database and Redis.
 */
public final class ReplayCli {

    private static final String TOPIC = "loan.applications.v1";
    private static final int NEW = 1000, REDELIVER = 50, CONFLICT = 10, MALFORMED = 10;

    private ReplayCli() {
    }

    public static void main(String[] args) throws Exception {
        String bootstrap = args.length > 0 ? args[0] : "localhost:9092";
        String baseUrl = args.length > 1 ? args[1] : "http://localhost:8080";
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        List<ApplicationRequest> apps = new SyntheticApplicationGenerator(7L,
                Instant.now().minus(Duration.ofHours(2)), "RPL" + Instant.now().getEpochSecond()).generate(NEW);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all"))) {
            for (ApplicationRequest app : apps) {
                producer.send(new ProducerRecord<>(TOPIC, app.applicationId(), json.writeValueAsString(app)));
            }
            for (int i = 0; i < REDELIVER; i++) {
                ApplicationRequest app = apps.get(i * 7);
                producer.send(new ProducerRecord<>(TOPIC, app.applicationId(), json.writeValueAsString(app)));
            }
            for (int i = 0; i < CONFLICT; i++) {
                ApplicationRequest app = apps.get(500 + i * 11);
                ApplicationRequest changed = new ApplicationRequest(app.applicationId(), app.applicantName(),
                        app.dob(), app.pan(), app.phone(), app.deviceId(),
                        app.requestedAmount().add(BigDecimal.ONE), app.declaredMonthlyIncome(), app.kyc(),
                        app.bureau(), app.submittedAt());
                producer.send(new ProducerRecord<>(TOPIC, app.applicationId(), json.writeValueAsString(changed)));
            }
            for (int i = 0; i < MALFORMED; i++) {
                producer.send(new ProducerRecord<>(TOPIC, "MALFORMED-" + i, "{\"applicationId\": broken " + i));
            }
            producer.flush();
        }
        System.out.printf("Published %d messages; waiting for the service to drain...%n",
                NEW + REDELIVER + CONFLICT + MALFORMED);

        HttpClient http = HttpClient.newHttpClient();
        long deadline = System.currentTimeMillis() + 180_000;
        double evaluated = 0, duplicates = 0, dlt = 0;
        while (System.currentTimeMillis() < deadline) {
            evaluated = metric(http, json, baseUrl, "doorstep.ingest.evaluated");
            duplicates = metric(http, json, baseUrl, "doorstep.ingest.duplicates");
            dlt = metric(http, json, baseUrl, "doorstep.ingest.dlt");
            if (evaluated + duplicates + dlt >= NEW + REDELIVER + CONFLICT + MALFORMED) {
                break;
            }
            Thread.sleep(1000);
        }
        double cases = metric(http, json, baseUrl, "doorstep.cases.created");
        double p95Seconds = metric(http, json, baseUrl, "doorstep.evaluation.latency.percentile?tag=phi:0.95");
        System.out.printf("""
                Applications evaluated : %.0f
                Duplicates skipped     : %.0f
                Sent to DLT            : %.0f
                Cases created          : %.0f
                p95 evaluation latency : %.1f ms
                """, evaluated, duplicates, dlt, cases, p95Seconds * 1000);
    }

    private static double metric(HttpClient http, ObjectMapper json, String baseUrl, String name) throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/actuator/metrics/" + name)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return 0;
        }
        JsonNode measurements = json.readTree(response.body()).get("measurements");
        return measurements.get(0).get("value").asDouble();
    }
}
