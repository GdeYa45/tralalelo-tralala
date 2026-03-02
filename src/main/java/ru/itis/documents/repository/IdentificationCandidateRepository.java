package ru.itis.documents.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itis.documents.domain.entity.IdentificationCandidate;

import java.util.List;

/**
 * Этап 9.1 (P0): стандартный Spring Data репозиторий.
 * CRUD обеспечивается JpaRepository.
 */
public interface IdentificationCandidateRepository extends JpaRepository<IdentificationCandidate, Long> {

    List<IdentificationCandidate> findAllByIdentification_IdOrderByScoreDesc(Long identificationId);
}