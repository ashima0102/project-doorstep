package com.azentio.doorstep.api;

import com.azentio.doorstep.TestFixtures;
import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationApiTest {

    private static final String FULL_PAN = "QWERT6789Z";
    private static final String FULL_PHONE = "9123456789";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private String submit(ApplicationRequest request) throws Exception {
        return mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void validApplicationReturnsDecisionScoreAndRuleHits() throws Exception {
        String body = submit(TestFixtures.clean().id("API-1").pan("ZXCVB1111Q").phone("9000000001")
                .device("dev-api-1").submittedAt(Instant.now()).build());
        JsonNode result = json.readTree(body);
        assertThat(result.get("decision").asText()).isEqualTo("PASS");
        assertThat(result.get("score").asInt()).isZero();
        assertThat(result.get("ruleHits").isArray()).isTrue();
    }

    @Test
    void invalidApplicationReturns400WithFieldErrors() throws Exception {
        String invalid = """
                {"applicationId":"BAD-1","applicantName":"X","dob":"1990-01-01","pan":"abc123",
                 "phone":"9000000002","deviceId":"d","requestedAmount":1000,"declaredMonthlyIncome":100,
                 "bureau":{"historyMonths":3},"submittedAt":"2026-11-14T06:00:00Z"}""";
        mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='pan')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='kyc')]").exists());
    }

    @Test
    void caseEndpointsNeverExposeFullPanOrPhone() throws Exception {
        // KYC mismatch (30) + inflated income (20) = 50 -> REVIEW -> opens a case
        String body = submit(TestFixtures.clean().id("API-CASE-1").pan(FULL_PAN).phone(FULL_PHONE)
                .device("dev-api-case").verified(false).bureauIncome("10000").declaredIncome("90000")
                .submittedAt(Instant.now()).build());
        assertThat(body).doesNotContain(FULL_PAN).doesNotContain(FULL_PHONE);
        String caseId = json.readTree(body).get("caseId").asText();

        String list = mvc.perform(get("/api/cases").param("status", "OPEN"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String detail = mvc.perform(get("/api/cases/{id}", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleHits.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(list).contains("******789Z").contains("******6789")
                .doesNotContain(FULL_PAN).doesNotContain(FULL_PHONE);
        assertThat(detail).contains("******789Z").contains("******6789")
                .doesNotContain(FULL_PAN).doesNotContain(FULL_PHONE);
    }

    @Test
    void unknownCaseReturns404() throws Exception {
        mvc.perform(get("/api/cases/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}
