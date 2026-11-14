package com.azentio.doorstep.persistence;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/** Deliberately not a CrudRepository: there is no update or delete path for audit rows. */
public interface CaseAuditRepository extends Repository<CaseAudit, Long> {

    CaseAudit save(CaseAudit audit);

    List<CaseAudit> findByCaseIdOrderByAtAsc(UUID caseId);
}
