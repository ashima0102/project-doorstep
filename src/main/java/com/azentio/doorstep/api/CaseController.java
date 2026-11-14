package com.azentio.doorstep.api;

import com.azentio.doorstep.api.dto.CaseDetail;
import com.azentio.doorstep.api.dto.CaseSummary;
import com.azentio.doorstep.persistence.CaseStatus;
import com.azentio.doorstep.service.CaseQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private final CaseQueryService queries;

    public CaseController(CaseQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public List<CaseSummary> list(@RequestParam(required = false) CaseStatus status) {
        return queries.list(status);
    }

    @GetMapping("/{caseId}")
    public CaseDetail get(@PathVariable UUID caseId) {
        return queries.get(caseId);
    }
}
