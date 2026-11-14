package com.azentio.doorstep;

import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.azentio.doorstep.config.FraudProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/** Synthetic test data only: PANs and phones here are made up. */
public final class TestFixtures {

    public static final Instant NOW = Instant.parse("2026-11-14T06:00:00Z");

    private TestFixtures() {
    }

    public static FraudProperties defaultProperties() {
        return new FraudProperties(
                new FraudProperties.Thresholds(30, 60),
                new FraudProperties.Rules(
                        new FraudProperties.PanReused(60, Duration.ofDays(30)),
                        new FraudProperties.SharedIdentifier(35, Duration.ofDays(30), 3),
                        new FraudProperties.SharedIdentifier(35, Duration.ofHours(24), 3),
                        new FraudProperties.Points(30),
                        new FraudProperties.ThinFile(15, 6, new BigDecimal("300000")),
                        new FraudProperties.IncomeInflated(20, new BigDecimal("3"))));
    }

    /** A clean applicant: verified KYC, healthy bureau file, modest amount, honest income. */
    public static Builder clean() {
        return new Builder();
    }

    public static final class Builder {
        private String applicationId = "APP-0001";
        private String name = "Asha Verma";
        private LocalDate dob = LocalDate.of(1990, 5, 17);
        private String pan = "ABCDE1234F";
        private String phone = "9876543210";
        private String deviceId = "dev-0001";
        private BigDecimal amount = new BigDecimal("150000");
        private BigDecimal declaredIncome = new BigDecimal("60000");
        private String nameOnId = "Asha Verma";
        private LocalDate dobOnId = dob;
        private boolean verified = true;
        private Integer bureauScore = 760;
        private int historyMonths = 48;
        private BigDecimal bureauIncome = new BigDecimal("55000");
        private Instant submittedAt = NOW;

        public Builder id(String v) { applicationId = v; return this; }
        public Builder name(String v) { name = v; nameOnId = v; return this; }
        public Builder dob(LocalDate v) { dob = v; dobOnId = v; return this; }
        public Builder pan(String v) { pan = v; return this; }
        public Builder phone(String v) { phone = v; return this; }
        public Builder device(String v) { deviceId = v; return this; }
        public Builder amount(String v) { amount = new BigDecimal(v); return this; }
        public Builder declaredIncome(String v) { declaredIncome = new BigDecimal(v); return this; }
        public Builder nameOnId(String v) { nameOnId = v; return this; }
        public Builder dobOnId(LocalDate v) { dobOnId = v; return this; }
        public Builder verified(boolean v) { verified = v; return this; }
        public Builder bureauScore(Integer v) { bureauScore = v; return this; }
        public Builder historyMonths(int v) { historyMonths = v; return this; }
        public Builder bureauIncome(String v) { bureauIncome = v == null ? null : new BigDecimal(v); return this; }
        public Builder submittedAt(Instant v) { submittedAt = v; return this; }

        public ApplicationRequest build() {
            return new ApplicationRequest(applicationId, name, dob, pan, phone, deviceId, amount, declaredIncome,
                    new ApplicationRequest.Kyc(nameOnId, dobOnId, verified),
                    new ApplicationRequest.Bureau(bureauScore, historyMonths, bureauIncome),
                    submittedAt);
        }
    }
}
