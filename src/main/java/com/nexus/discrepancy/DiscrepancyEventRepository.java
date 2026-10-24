package com.nexus.discrepancy;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscrepancyEventRepository extends JpaRepository<DiscrepancyEvent, UUID> {

    List<DiscrepancyEvent> findByDiscrepancyIdOrderByCreatedAtAsc(UUID discrepancyId);
}
