package com.azentio.doorstep.generator;

import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generates synthetic applications and POSTs them to the running service, then prints counts per
 * decision and per rule. Usage: scripts/generate.sh [count] [baseUrl]
 */
public final class GeneratorCli {

    private GeneratorCli() {
    }

    public static void main(String[] args) throws Exception {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 500;
        String baseUrl = args.length > 1 ? args[1] : "http://localhost:8080";
        String prefix = "GEN" + Instant.now().getEpochSecond();

        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        List<ApplicationRequest> applications =
                new SyntheticApplicationGenerator(42L, Instant.now().minus(Duration.ofHours(1)), prefix).generate(count);

        Map<String, Integer> decisions = new TreeMap<>();
        Map<String, Integer> rules = new TreeMap<>();
        int cases = 0;
        for (ApplicationRequest application : applications) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/applications"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(application)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.printf("%s -> HTTP %d %s%n", application.applicationId(), response.statusCode(), response.body());
                continue;
            }
            JsonNode body = json.readTree(response.body());
            decisions.merge(body.get("decision").asText(), 1, Integer::sum);
            body.get("ruleHits").forEach(hit -> rules.merge(hit.get("ruleId").asText(), 1, Integer::sum));
            if (!body.get("caseId").isNull()) {
                cases++;
            }
        }
        System.out.printf("Posted %d applications%nDecisions: %s%nRule hits: %s%nCases created: %d%n",
                applications.size(), decisions, rules, cases);
    }
}
