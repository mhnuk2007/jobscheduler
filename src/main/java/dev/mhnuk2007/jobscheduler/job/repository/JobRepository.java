package dev.mhnuk2007.jobscheduler.job.repository;

import dev.mhnuk2007.jobscheduler.job.domain.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {
    Optional<Job> findByJobId(String jobId);
    Optional<Job> findByIdempotencyKey(String idempotencyKey);
}