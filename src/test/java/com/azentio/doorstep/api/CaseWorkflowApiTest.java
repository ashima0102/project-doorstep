package com.azentio.doorstep.api;

import com.azentio.doorstep.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CaseWorkflowApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private String openCase(String id, String pan, String phone) throws Exception {
        String body = mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(TestFixtures.clean().id(id).pan(pan).phone(phone)
                                .device("dev-" + id).verified(false).submittedAt(Instant.now()).build())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("caseId").asText();
    }

    private org.springframework.test.web.servlet.ResultActions act(String caseId, String action, String comment)
            throws Exception {
        return mvc.perform(post("/api/cases/{id}/actions", caseId).header("X-User", "uw.priya")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"%s\",\"comment\":\"%s\"}".formatted(action, comment)));
    }

    @Test
    void validTransitionUpdatesStatusAndWritesAuditRow() throws Exception {
        String caseId = openCase("WF-1", "WORKF1111A", "9555555551");

        act(caseId, "APPROVE", "Documents re-verified by phone call")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(get("/api/cases/{id}/audit", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fromStatus").value("OPEN"))
                .andExpect(jsonPath("$[0].toStatus").value("APPROVED"))
                .andExpect(jsonPath("$[0].actor").value("uw.priya"));
    }

    @Test
    void actingOnAnApprovedCaseReturns409() throws Exception {
        String caseId = openCase("WF-2", "WORKF2222B", "9555555552");
        act(caseId, "APPROVE", "Documents re-verified by phone call").andExpect(status().isOk());

        act(caseId, "REJECT", "Changing my mind after approval")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot REJECT a case that is APPROVED"));
    }

    @Test
    void shortCommentReturns400AndWritesNoAudit() throws Exception {
        String caseId = openCase("WF-3", "WORKF3333C", "9555555553");

        act(caseId, "ESCALATE", "too short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("comment"));
        mvc.perform(get("/api/cases/{id}/audit", caseId)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void escalatedCaseCanStillBeRejected() throws Exception {
        String caseId = openCase("WF-4", "WORKF4444D", "9555555554");
        act(caseId, "ESCALATE", "Needs senior review of device data").andExpect(status().isOk());
        act(caseId, "REJECT", "Senior review confirms device ring").andExpect(jsonPath("$.status").value("REJECTED"));
    }
}
