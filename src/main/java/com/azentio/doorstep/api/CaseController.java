package com.azentio.doorstep.api;

import com.azentio.doorstep.api.dto.AuditEntry;
import com.azentio.doorstep.api.dto.CaseActionRequest;
import com.azentio.doorstep.api.dto.CaseDetail;
import com.azentio.doorstep.api.dto.CaseSummary;
import com.azentio.doorstep.persistence.CaseStatus;
import com.azentio.doorstep.service.CaseQueryService;
import com.azentio.doorstep.service.CaseWorkflowService;
import com.azentio.doorstep.service.InvalidRequestException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private final CaseQueryService queries;
    private final CaseWorkflowService workflow;

    public CaseController(CaseQueryService queries, CaseWorkflowService workflow) {
        this.queries = queries;
        this.workflow = workflow;
    }

    @GetMapping
    public List<CaseSummary> list(@RequestParam(required = false) CaseStatus status) {
        return queries.list(status);
    }

    @GetMapping("/{caseId}")
    public CaseDetail get(@PathVariable UUID caseId) {
        return queries.get(caseId);
    }

    @PostMapping("/{caseId}/actions")
    public CaseDetail act(@PathVariable UUID caseId,
                          @RequestHeader("X-User") String actor,
                          @Valid @RequestBody CaseActionRequest request) {
        if (actor.isBlank()) {
            throw new InvalidRequestException("X-User header must name the acting underwriter");
        }
        workflow.act(caseId, request.action(), actor.trim(), request.comment());
        return queries.get(caseId);
    }

    @GetMapping("/{caseId}/audit")
    public List<AuditEntry> audit(@PathVariable UUID caseId) {
        return workflow.history(caseId);
    }
}
