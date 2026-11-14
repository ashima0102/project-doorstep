package com.azentio.doorstep.service;

import com.azentio.doorstep.api.dto.EvaluationResponse;

/** duplicate = an exact redelivery of an already evaluated application; nothing new was written. */
public record EvaluationOutcome(EvaluationResponse response, boolean duplicate) {
}
