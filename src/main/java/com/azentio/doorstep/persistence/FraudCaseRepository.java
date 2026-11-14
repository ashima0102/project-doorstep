package com.azentio.doorstep.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FraudCaseRepository extends JpaRepository<FraudCase, UUID> {

    /** Served by idx_fraud_case_status_score. */
    List<FraudCase> findByStatusOrderByScoreDescCreatedAtAsc(CaseStatus status);

    List<FraudCase> findAllByOrderByScoreDescCreatedAtAsc();

    Optional<FraudCase> findByApplicationId(String applicationId);
}
