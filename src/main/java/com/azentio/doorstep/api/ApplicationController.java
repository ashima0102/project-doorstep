package com.azentio.doorstep.api;

import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.azentio.doorstep.api.dto.EvaluationResponse;
import com.azentio.doorstep.service.ApplicationEvaluationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationEvaluationService service;

    public ApplicationController(ApplicationEvaluationService service) {
        this.service = service;
    }

    @PostMapping
    public EvaluationResponse submit(@Valid @RequestBody ApplicationRequest request) {
        return service.evaluate(request).response();
    }
}
