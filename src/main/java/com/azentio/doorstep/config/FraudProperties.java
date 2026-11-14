package com.azentio.doorstep.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Every rule weight, window, count limit and decision threshold. Bound from the {@code fraud.*}
 * section of application.yml so tuning never needs a code change.
 */
@ConfigurationProperties(prefix = "fraud")
public record FraudProperties(Thresholds thresholds, Rules rules) {

    public record Thresholds(int review, int reject) {
    }

    public record Rules(PanReused panReused,
                        SharedIdentifier phoneShared,
                        SharedIdentifier deviceShared,
                        Points kycMismatch,
                        ThinFile thinFile,
                        IncomeInflated incomeInflated) {
    }

    public record Points(int points) {
    }

    public record PanReused(int points, Duration window) {
    }

    public record SharedIdentifier(int points, Duration window, int minDistinctPans) {
    }

    public record ThinFile(int points, int minHistoryMonths, BigDecimal amountAbove) {
    }

    public record IncomeInflated(int points, BigDecimal multiplier) {
    }
}
