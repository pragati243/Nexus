package com.nexus.processing;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingAttemptRepository extends JpaRepository<ProcessingAttempt, UUID> {

    List<ProcessingAttempt> findByIngestionJobIdOrderByAttemptNumberAsc(UUID ingestionJobId);
}
