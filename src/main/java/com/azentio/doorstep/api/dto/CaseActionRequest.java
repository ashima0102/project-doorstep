package com.azentio.doorstep.api.dto;

import com.azentio.doorstep.persistence.CaseAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CaseActionRequest(@NotNull CaseAction action,
                                @NotBlank @Size(min = 10, max = 1000, message = "must be at least 10 characters") String comment) {
}
