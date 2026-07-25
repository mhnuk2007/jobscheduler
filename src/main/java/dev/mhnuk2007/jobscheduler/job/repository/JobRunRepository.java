package dev.mhnuk2007.jobscheduler.job.repository;

import dev.mhnuk2007.jobscheduler.job.domain.JobRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRunRepository extends JpaRepository<JobRun, Long> {
    List<JobRun> findByJobIdOrderByAttemptAsc(Long jobId);
    int countByJobId(Long jobId);
}
