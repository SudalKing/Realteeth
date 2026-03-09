# Image Processor

비동기 이미지 처리 시스템 - Transactional Outbox 패턴 기반

## 기술 스택

| 구분              | 기술                                   |
|-----------------|--------------------------------------|
| Language        | Kotlin 1.9.23                        |
| Framework       | Spring Boot 3.2.4                    |
| ORM             | Spring Data JPA + Hibernate          |
| Database        | MySQL 8.0                            |
| Test & Coverage | JUnit 5, Mockito-Kotlin, H2 & Jacoco |
| API Docs        | SpringDoc OpenAPI (Swagger)          |
| Infra           | Docker, Docker Compose               |

---

## 실행 방법

### 환경별 구성

| 환경 | 프로필 | 앱 실행 | MySQL 컨테이너 | 호스트 포트 |
|------|--------|---------|---------------|------------|
| **Local** | `local` | IDE/CLI | `mysql-local` | 3306 |
| **Dev** | `dev` | Docker | `mysql-dev` | 3307 |

### Local 환경 (IDE/CLI 로컬 실행용)

```bash
# 1. MySQL 컨테이너 실행
docker-compose -f docker-compose.local.yml up -d

# 2. 애플리케이션 실행
./gradlew bootRun --args='--spring.profiles.active=local'

# 3. 종료
docker-compose -f docker-compose.local.yml down # 컨테이너만
docker-compose -f docker-compose.local.yml down -v # 데이터까지 삭제
```

### Dev 환경 (Docker 전체 실행)

```bash
# 1. 빌드 및 실행
docker-compose -f docker-compose.dev.yml up -d --build

# 2. 로그 확인
docker-compose -f docker-compose.dev.yml logs -f app

# 3. 종료
docker-compose -f docker-compose.dev.yml down # 컨테이너만
docker-compose -f docker-compose.dev.yml down -v # 데이터까지 삭제
```

### 접속 정보

| 항목 | URL |
|------|-----|
| API 서버 | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/api-docs |

---

## API 명세

### 1. 이미지 처리 작업 요청

```bash
POST /api/v1/tasks
Content-Type: application/json

{
  "image_url": "https://example.com/image.jpg",
  "idempotency_key": "unique-request-id-123"
}
```

**Response (202 Accepted)**
```json
{
  "task_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "message": "작업이 생성되었습니다."
}
```

**cURL 예시**
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "image_url": "https://example.com/image.jpg",
    "idempotency_key": "test-001"
  }'
```

### 2. 작업 상태 조회

```bash
GET /api/v1/tasks/{taskId}
```

**Response**
```json
{
  "task_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "result": "Image processed successfully",
  "error_message": null,
  "created_at": "2024-01-01T10:00:00",
  "updated_at": "2024-01-01T10:00:30"
}
```

### 3. 작업 목록 조회

```bash
# 전체 목록
GET /api/v1/tasks

# 상태별 필터링
GET /api/v1/tasks?status=PENDING,PROCESSING
```

**Response**
```json
{
  "tasks": [...],
  "total_count": 10
}
```

---

## 테스트

### 테스트 실행 & Jacoco 커버리지 보고서

```bash
# 테스트 + 커버리지 보고서 생성
./gradlew test jacocoTestReport

# 보고서 열기 (Mac)
open build/reports/jacoco/test/html/index.html

# 보고서 열기 (Windows)
start build/reports/jacoco/test/html/index.html
```

---
## 설계 문서

### [4.1] 중복 요청 처리

**멱등성 보장**

1. **Pessimistic Lock 사용**
   ```kotlin
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   fun findByTaskIdWithLock(taskId: String): Optional<ImageTask>
   ```
   - 상태 변경 시 반드시 Lock 획득 후 처리
   - 동시에 같은 Task 상태 변경 시도 시 순차 처리


2. **Idempotency Key UNIQUE 제약**
   ```kotlin
   // 요청 시 클라이언트가 고유 키 제공
   {
     "image_url": "...",
     "idempotency_key": "client-generated-unique-id"
   }
   ```
   - DB 레벨에서 중복 삽입 방지
   - 동시에 같은 키로 요청해도 하나만 성공

**처리 방식**
1. `idempotency_key`에 UNIQUE 제약 조건 설정
2. 요청 수신 시 기존 키 존재 여부 확인
3. 존재하면 기존 작업 반환
4. 존재하지 않으면 새 작업 요청

**설계 의도**
- 클라이언트가 요청 시 동일한 키를 전송하면 중복 처리 방지
- 네트워크 타임아웃 등으로 응답을 못 받은 경우에도 안전

---

### [4.2] 상태 전이

#### **상태 흐름도**

```
┌─────────┐     ┌────────────┐     ┌───────────┐
│ PENDING │────▶│ PROCESSING │────▶│ COMPLETED │
└─────────┘     └────────────┘     └───────────┘
     │                │
     │                │
     ▼                ▼
┌─────────────────────────────┐
│           FAILED            │
└─────────────────────────────┘
```

#### **상태 정의**

| 상태 | 설명                       |
|------|--------------------------|
| PENDING | 요청 접수, Mock Worker 전송 대기 |
| PROCESSING | Mock Worker 처리 중         |
| COMPLETED | 처리 완료 (최종 상태)            |
| FAILED | 처리 실패 (최종 상태)            |

#### **허용되는 상태 전이**
- PENDING -> PROCESSING (Mock Worker 요청 성공)
- PENDING -> FAILED (Mock Worker 요청 실패, 재시도 초과)
- PROCESSING -> COMPLETED (처리 성공)
- PROCESSING -> FAILED (처리 실패)

#### **허용되지 않는 상태 전이**
- PENDING -> COMPLETED (직접 완료 불가, 반드시 PROCESSING 거쳐야 함)
- COMPLETED -> X (최종 상태, 전이 불가)
- FAILED -> X (최종 상태, 전이 불가)
- 역방향 전이 모두 불가

#### **설계 의도**
PENDING 상태를 둔 이유는 "요청 수신"과 "외부 시스템 호출"을 분리하기 위함입니다.
1. 클라이언트는 즉시 응답을 받을 수 있음
2. Mock Worker 장애 시에도 요청이 유실되지 않음
3. 재시도 로직을 손쉽게 구현 가능

---
### [4.3] 처리 보장 모델

#### **Transaction Outbox 패턴**

```
┌───────────────────────────────────────────────────────────┐
│                   Single Transaction                      │
│  ┌─────────────────┐            ┌─────────────────┐       │
│  │  image_task     │            │     outbox      │       │
│  │    INSERT       │            │     INSERT      │       │
│  └─────────────────┘            └─────────────────┘       │
└───────────────────────────────────────────────────────────┘
                              │
                              ▼
                    @TransactionalEventListener
                        (AFTER_COMMIT)
                              │
                              ▼
                      ┌─────────────────┐
                      │   Mock Worker   │
                      │    API Call     │
                      └─────────────────┘
```

#### **설계 의도**
1. **원자성 보장**: 이미지 처리 작업 저장과 이벤트 발행이 같은 트랜잭션에서 처리
2. **유실 방지**: DB에 저장되므로 서버 재시작해도 이벤트 유실 없음
3. **재시도 용이**: 스케줄러가 미처리 이벤트를 주기적으로 재처리
4. **미들웨어 메세지 큐없이 구현**: 단일 MySQL만으로 신뢰성 있는 비동기 처리 가능

#### **At-Least-Once** 모델

**At-Least-Once인 이유**
- Outbox 레코드가 처리 완료되기 전에 서버가 재시작하면, 스케줄러가 해당 이벤트를 다시 처리 
- Mock Worker 호출 성공 후 작업 상태 업데이트 전에 실패하면, 동일 작업이 재전송될 수 있음
- 멱등성 키로 중복 요청은 방지하지만, Mock Worker 측에서도 멱등성을 보장해야 완전한 exactly-once가 가능

**Exactly-Once가 아닌 이유**
- Mock Worker 호출과 Outbox 상태 업데이트 사이에 실패 지점 존재
- 분산 트랜잭션 없이는 완전한 exactly-once 모델이 힘듦

---
### [4.4] 서버 재시작 시 동작

#### **복구 프로세스**

```
서버 시작
    │
    ▼
스케줄러 활성화 (initialDelay 후)
    │
    ├──▶ OutboxScheduler: PENDING 상태 Outbox 재처리
    │
    ├──▶ JobPollingScheduler: PROCESSING 상태 작업 폴링 재시작
    │
    └──▶ StuckTaskRecoveryScheduler: 오래된(2분) PENDING 작업 복구
```

#### **데이터 정합성 위험 지점**

1. **Mock Worker 호출 성공 직후 서버 다운**
    - Outbox가 여전히 PENDING 상태이므로 서버 재시작 후 복구 작업으로 재처리됨
    - Mock Worker에 중복 요청 발생 가능
    - **대응**: Mock Worker도 멱등성 보장 필요

2. **Task PROCESSING 중 서버 다운**
    - 재시작 후 JobPollingScheduler가 상태 폴링 재개
    - Mock Worker 결과 정상 반영됨

3. **폴링 결과 반영 직전 서버 다운**
    - 재시작 후 다시 폴링하여 상태 반영
    - 데이터 유실 없음

**결론**: 중복 처리 가능성은 있으나, 데이터 유실은 발생하지 않음

---
### 실패 처리 전략

#### **재시도 정책**
| 구분 | 최대 재시도 | 재시도 간격  |
|------|------------|---------|
| Outbox 이벤트 | 5회 | 10초     |
| Task 자체 | 3회 | 스케줄러 주기 |

#### **실패 케이스별 처리**
**Mock Worker 장애 또는 네트워크 오류**
   - Outbox 재시도 카운트 증가
   - 스케줄러가 주기적으로 재시도
   - 최대 재시도 초과 시 FAILED 처리

---

### 트래픽 증가 시 병목 지점

#### **예상 병목 지점**

1. **Database Connection Pool**
    - 현재: 기본 HikariCP 설정 (5개)
    - 대응: connection pool 크기 조정, read replica 도입 -> read/write 분리

2. **Outbox 테이블 조회**
    - 스케줄러가 주기적으로 전체 스캔
    - 대응: 인덱스 최적화, 파티셔닝, 처리 완료 레코드 별도 저장소에 아카이빙

3. **Mock Worker 호출**
    - 동기 HTTP 호출로 스레드 점유
    - 대응: WebClient 비동기 호출, connection pool 튜닝

4. **단일 인스턴스 스케줄러**
    - 현재 단일 서버에서만 스케줄러 동작
    - 대응: ShedLock, Redis 등으로 분산 락 적용, 혹은 Redis Streams, Kafka를 활용한 Event 기반 아키텍처로 전환

**서비스 확장 시 아키텍처**

```
현재 (v1.0)       →     확장 (v2.0)
──────────────────────────────────────────
Spring Event           Kafka/Redis Streams
+ Outbox 패턴           + Consumer Groups
+ DB 스케줄러            + 파티션 병렬 처리
```

---

### 외부 시스템 연동 방식

#### **Polling 방식**

Mock Worker가 Webhook을 제공하는지 모르기에, Polling 방식으로 결과 조회

```
┌─────────────┐     POST /process      ┌─────────────┐
│   Server    │ ───────────────────▶   │ Mock Worker │
│             │      { jobId }         │             │
│             │ ◀───────────────────   │             │
│             │ ┌───────────────────┐  │             │
│             │ │ GET /process/{id} │  │             │
│             │ │──────────────────▶│  │             │
│             │ │ { status, result }│  │             │
│             │ │◀──────────────────│  │             │
└─────────────┘ └───────────────────┘  └─────────────┘
                          │
                          │ (5초 주기 폴링)
                          ▼
```

#### **선택 이유**
- Mock Worker API 스펙에 Webhook이 없음
- Polling은 구현이 단순하고 신뢰성이 높음
- 5초 간격으로 부하 최소화

---
## 설정 파일 구조

| 파일 | 용도 |
|------|------|
| `application.yml` | 공통 설정 (JPA, Jackson, Swagger 등) |
| `application-local.yml` | Local 환경 (localhost:3306 접속) |
| `application-dev.yml` | Dev 환경 (Docker 내부 mysql-dev:3306 접속) |
| `application-test.yml` | 테스트 환경 (H2 인메모리 DB) |
| `docker-compose.local.yml` | Local용 MySQL 컨테이너 |
| `docker-compose.dev.yml` | Dev용 앱 + MySQL 컨테이너 |
