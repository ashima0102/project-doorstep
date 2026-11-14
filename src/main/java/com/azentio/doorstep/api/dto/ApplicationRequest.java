package com.azentio.doorstep.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Incoming loan application with KYC, bureau and device data. Shared by the REST and Kafka entry points. */
public record ApplicationRequest(
        @NotBlank String applicationId,
        @NotBlank String applicantName,
        @NotNull LocalDate dob,
        @NotBlank @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]", message = "must match [A-Z]{5}[0-9]{4}[A-Z]") String pan,
        @NotBlank @Pattern(regexp = "[0-9]{10}", message = "must be 10 digits") String phone,
        @NotBlank String deviceId,
        @NotNull @Positive BigDecimal requestedAmount,
        @NotNull @PositiveOrZero BigDecimal declaredMonthlyIncome,
        @NotNull @Valid Kyc kyc,
        @NotNull @Valid Bureau bureau,
        @NotNull Instant submittedAt) {

    public record Kyc(@NotBlank String nameOnId, @NotNull LocalDate dobOnId, @NotNull Boolean verified) {
    }

    /** score and estimatedMonthlyIncome are nullable: a thin-file applicant may have neither. */
    public record Bureau(Integer score,
                         @NotNull @PositiveOrZero Integer historyMonths,
                         @PositiveOrZero BigDecimal estimatedMonthlyIncome) {
    }
}
