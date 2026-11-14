package com.azentio.doorstep.persistence;

import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.azentio.doorstep.rules.Decision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Full PAN and phone are stored here (the system of record); they are masked on every way out. */
@Entity
@Table(name = "loan_application")
public class LoanApplication {

    @Id
    private String applicationId;
    private String applicantName;
    private LocalDate dob;
    private String pan;
    private String phone;
    private String deviceId;
    private BigDecimal requestedAmount;
    private BigDecimal declaredMonthlyIncome;
    private Instant submittedAt;
    @Enumerated(EnumType.STRING)
    private Decision decision;
    private int score;
    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    protected LoanApplication() {
    }

    public LoanApplication(ApplicationRequest request, Decision decision, int score, Instant evaluatedAt) {
        this.applicationId = request.applicationId();
        this.applicantName = request.applicantName();
        this.dob = request.dob();
        this.pan = request.pan();
        this.phone = request.phone();
        this.deviceId = request.deviceId();
        this.requestedAmount = request.requestedAmount();
        this.declaredMonthlyIncome = request.declaredMonthlyIncome();
        this.submittedAt = request.submittedAt();
        this.decision = decision;
        this.score = score;
        this.evaluatedAt = evaluatedAt;
    }

    public String getApplicationId() { return applicationId; }
    public String getApplicantName() { return applicantName; }
    public LocalDate getDob() { return dob; }
    public String getPan() { return pan; }
    public String getPhone() { return phone; }
    public String getDeviceId() { return deviceId; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public BigDecimal getDeclaredMonthlyIncome() { return declaredMonthlyIncome; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Decision getDecision() { return decision; }
    public int getScore() { return score; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
}
