
-- image_task: 이미지 처리 작업 테이블
CREATE TABLE IF NOT EXISTS image_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL UNIQUE COMMENT '외부 노출용 작업 ID (UUID)',
    image_url VARCHAR(2048) NOT NULL COMMENT '처리할 이미지 URL',
    idempotency_key VARCHAR(64) NOT NULL UNIQUE COMMENT '중복 요청 방지용 키',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '작업 상태: PENDING, PROCESSING, COMPLETED, FAILED',
    mock_job_id VARCHAR(100) NULL COMMENT 'Mock Worker에서 발급한 Job ID',
    result TEXT NULL COMMENT '처리 결과',
    error_message TEXT NULL COMMENT '에러 메시지',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '재시도 횟수',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    INDEX idx_task_id (task_id),
    INDEX idx_idempotency_key (idempotency_key),
    INDEX idx_status (status),
    INDEX idx_status_updated (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- outbox: Transaction Outbox 패턴을 위한 테이블
CREATE TABLE IF NOT EXISTS outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL COMMENT '집계 타입 (ex: ImageTask)',
    aggregate_id VARCHAR(36) NOT NULL COMMENT '집계 ID (ex: task_id)',
    event_type VARCHAR(50) NOT NULL COMMENT '이벤트 타입 (ex: TASK_CREATED)',
    payload JSON NOT NULL COMMENT '이벤트 페이로드',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '처리 상태: PENDING, PROCESSED, FAILED',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '재시도 횟수',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    processed_at DATETIME NULL COMMENT '처리 완료 시각',

    INDEX idx_outbox_status (status),
    INDEX idx_outbox_created_at (created_at),
    INDEX idx_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;