CREATE TABLE jobs (
                      id                  BIGSERIAL PRIMARY KEY,
                      job_id              VARCHAR(64)     NOT NULL UNIQUE,
                      owner_id            VARCHAR(128)    NOT NULL,
                      type                VARCHAR(16)     NOT NULL,
                      status              VARCHAR(16)     NOT NULL,
                      run_at              TIMESTAMPTZ,
                      cron_expression     VARCHAR(64),
                      next_run_at         TIMESTAMPTZ     NOT NULL,
                      max_retries         INT             NOT NULL DEFAULT 3,
                      timeout_seconds     INT             NOT NULL DEFAULT 30,
                      idempotency_key     VARCHAR(128),
                      claimed_by          VARCHAR(128),
                      claimed_at          TIMESTAMPTZ,

                      callback_url        TEXT            NOT NULL,
                      callback_method     VARCHAR(8)      NOT NULL DEFAULT 'POST',
                      callback_headers    JSONB,
                      callback_body       JSONB,

                      created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
                      updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

                      CONSTRAINT uq_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_jobs_claim_candidates
    ON jobs (next_run_at)
    WHERE status = 'SCHEDULED';

CREATE INDEX idx_jobs_owner ON jobs (owner_id);

CREATE TABLE job_runs (
                          id              BIGSERIAL PRIMARY KEY,
                          run_id          VARCHAR(64)     NOT NULL UNIQUE,
                          job_id          BIGINT          NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
                          attempt         INT             NOT NULL,
                          status          VARCHAR(16)     NOT NULL,
                          started_at      TIMESTAMPTZ     NOT NULL,
                          finished_at     TIMESTAMPTZ,
                          http_status     INT,
                          error_message   TEXT,

                          CONSTRAINT uq_job_attempt UNIQUE (job_id, attempt)
);

CREATE INDEX idx_job_runs_job_id ON job_runs (job_id);