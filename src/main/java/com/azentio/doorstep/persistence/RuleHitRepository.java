package com.azentio.doorstep.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleHitRepository extends JpaRepository<RuleHitRecord, Long> {

    List<RuleHitRecord> findByApplicationIdOrderByPointsDesc(String applicationId);

    long countByApplicationId(String applicationId);
}
