# U-Plus 스트리밍 플랫폼 — Back-end

> U+ 구독형 OTT 서비스를 위한 Spring Boot 기반 멀티모듈 백엔드

---

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [기술 스택](#2-기술-스택)
3. [모듈 구조](#3-모듈-구조)
4. [인프라 구성](#4-인프라-구성)
5. [핵심 도메인 & ERD 요약](#5-핵심-도메인--erd-요약)
6. [주요 기능](#6-주요-기능)
7. [장애 복원력 아키텍처](#7-장애-복원력-아키텍처)
8. [결제 멱등성](#8-결제-멱등성)
9. [API 엔드포인트 요약](#9-api-엔드포인트-요약)
10. [개발 환경 설정](#10-개발-환경-설정)
11. [환경 변수](#11-환경-변수)

---

## 1. 프로젝트 개요

U-Plus 백엔드는 **구독형 스트리밍 서비스**를 위한 REST API 서버입니다.
사용자 인증, 콘텐츠 검색/추천, 비디오 스트리밍, 결제·구독 관리, 관리자 콘텐츠 업로드 파이프라인을 제공합니다.

### 주요 특징

- **멀티모듈 Gradle** 구조로 도메인 / 보안 / 서비스 계층 분리
- **Redis + Elasticsearch + Kafka + MinIO** 혼합 인프라
- **장애 복원력 (Fault Tolerance)**: 외부 의존성 장애 시에도 핵심 기능 유지
- **Redis SETNX 기반 결제 멱등성**: 동시 중복 결제 원천 차단
- **하이브리드 벡터 검색**: 키워드 + 사용자 태그 벡터(kNN)를 결합한 개인화 검색

---

## 2. 기술 스택

| 분류 | 기술 |
|------|------|
| **언어 / 프레임워크** | Java 21, Spring Boot 4.0.2 |
| **ORM / DB** | Spring Data JPA (Hibernate), MySQL 8.4, Flyway |
| **검색** | Elasticsearch (Nori 한국어 형태소 분석기) |
| **캐시 / 분산 락** | Redis 7 (RedisTemplate, SETNX) |
| **메시지 큐** | Apache Kafka 3.7.1 (KRaft 단일 노드) |
| **오브젝트 스토리지** | MinIO (AWS S3 호환) |
| **보안** | Spring Security 6, JWT (Auth0 java-jwt), BCrypt |
| **소셜 로그인** | Google / Kakao / Naver OAuth2 |
| **이메일** | Gmail SMTP (JavaMailSender) |
| **API 문서** | Springdoc OpenAPI 3.0.1 (Swagger UI) |
| **컨테이너** | Docker, Docker Compose |
| **빌드** | Gradle Wrapper |

---

## 3. 모듈 구조

```
Back-end/
├── modules/
│   ├── domain/              # JPA 엔티티, Repository, 공유 Enum
│   ├── core/                # JWT 보안, MinIO 스토리지, Kafka 이벤트 발행
│   ├── user-api/            # 사용자용 REST API (port 8080)
│   ├── admin-api/           # 관리자용 REST API (port 8080)
│   └── transcoder-worker/   # 영상 트랜스코딩 Kafka Consumer
├── docker/                  # 서비스별 Dockerfile
├── docker-compose.yml
├── build.gradle
├── settings.gradle
└── research.md              # 기술 결정 및 장애 분석 문서
```

### 모듈 의존 관계

```
user-api   ─┐
admin-api  ─┤─→ core ─→ domain
transcoder ─┘
```

- **domain**: 엔티티, Repository 인터페이스 (공유 데이터 계층)
- **core**: JWT Provider/Filter, MinIO 서비스, Kafka Publisher (공유 인프라)
- **user-api**: 인증, 검색, 추천, 결제, 북마크, 댓글, 시청 이력 API
- **admin-api**: 콘텐츠/영상 CRUD, 사용자 관리, Presigned URL 발급
- **transcoder-worker**: Kafka Consumer → 영상 HLS 변환 → DB 상태 업데이트

---

## 4. 인프라 구성

```
┌─────────────────────────────────────────────────────┐
│                   Docker Compose                    │
│                                                     │
│  ┌──────────┐  ┌──────────┐  ┌─────────────────┐  │
│  │ user-api │  │admin-api │  │transcoder-worker│  │
│  │  :8080   │  │  :8080   │  │   (random port) │  │
│  └────┬─────┘  └────┬─────┘  └────────┬────────┘  │
│       │              │                  │            │
│  ┌────▼──────────────▼──────────────────▼────────┐  │
│  │                 MySQL 8.4                     │  │
│  │             (Flyway Migration)                │  │
│  └───────────────────────────────────────────────┘  │
│  ┌─────────┐  ┌───────────────┐  ┌─────────────┐  │
│  │ Redis 7 │  │Elasticsearch  │  │    MinIO    │  │
│  │  :6379  │  │(Nori 한국어)  │  │    :9000    │  │
│  └─────────┘  └───────────────┘  └─────────────┘  │
│  ┌─────────────────────────────────────────────┐   │
│  │         Apache Kafka 3.7.1 (KRaft)          │   │
│  └─────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

모든 서비스는 health check를 통과한 후 상위 서비스가 기동됩니다.

---

## 5. 핵심 도메인 & ERD 요약

### 사용자 도메인

| 엔티티 | 주요 필드 | 관계 |
|--------|----------|------|
| `User` | nickname (UK), userRole, userStatus | 1:N AuthAccount, 1:1 UserUplusVerified |
| `AuthAccount` | authProvider (EMAIL/GOOGLE/KAKAO/NAVER), email, passwordHash | N:1 User |
| `RefreshToken` | tokenValue, userId, expiresAt | — |
| `Subscriptions` | planType (SUB_BASIC), subscriptionStatus, startedAt, expiresAt | N:1 User |
| `Payment` | amount (4,900원), paymentStatus, paymentProvider, approvedAt | N:1 Subscriptions |
| `UserPreferredTag` | userId, tagId | — |
| `UserUplusVerified` | verifiedAt, telecomMemberId | 1:1 User |

### 콘텐츠 도메인

| 엔티티 | 주요 필드 | 관계 |
|--------|----------|------|
| `Content` | title, type, status (ACTIVE/INACTIVE), totalViewCount, accessLevel (FREE/PREMIUM) | 1:N Video, 1:N ContentTag |
| `Video` | episodeNo (content 내 UK), status (DRAFT/PUBLISHED), viewCount | N:1 Content, 1:1 VideoFile |
| `VideoFile` | hlsPath, mp4Path, transcodeStatus | 1:1 Video |
| `ContentTag` | contentId, tagId | — |
| `WatchHistory` | userId, contentId, videoId, lastPositionSec, status (소프트 삭제) | — |
| `Bookmark` | userId, contentId | — |
| `Comment` | videoId, body, status | N:1 User |

### 공통

- **`Tag`**: 콘텐츠 카테고리 / 사용자 선호 태그용
- **`BaseTimeEntity`**: 모든 엔티티에 createdAt, updatedAt (JPA Auditing)

---

## 6. 주요 기능

### 6-1. 인증 (Authentication)

**이메일 회원가입 — 4단계 플로우**

```
1. POST /api/auth/signup/email/send-code    → 이메일 OTP 발송 (Redis TTL 5분)
2. POST /api/auth/signup/email/verify-code  → OTP 검증 → Setup Token 발급 (10분)
3. POST /api/auth/signup/profile/nickname   → 닉네임 설정
4. POST /api/auth/signup/profile/tags       → 선호 태그 3개 선택 → 회원 가입 완료
```

**소셜 로그인 (Google / Kakao / Naver)**

```
POST /api/auth/login/google   → Code → User Info 조회 → User/AuthAccount Upsert → JWT 발급
POST /api/auth/login/kakao
POST /api/auth/login/naver
```

**JWT 구조**

| 토큰 | TTL | Claim |
|------|-----|-------|
| Access Token | 30분 | userId, email, nickname, role, type="access" |
| Refresh Token | 14일 | userId, type="refresh" (DB 저장) |
| Setup Token | 10분 | provider, email, passwordHash, type="setup" (회원가입 중간 단계) |

### 6-2. 콘텐츠 검색 & 추천

**Elasticsearch 기반 풀텍스트 검색**

- **Nori 한국어 형태소 분석기** + **N-gram 인덱스** 조합
- **초성 검색** 지원 (`ChosungUtil.extract()`)
- **하이브리드 kNN**: 사용자 선호 태그 벡터(30차원)와 키워드 검색 점수 합산
- **Function Score**: `totalViewCount` (log1p×0.1) + `bookmarkCount` (log1p×0.2) 가중치
- **필터**: category (MOVIE/SERIES), genre, tag 동시 적용
- **정렬**: 인기순(기본) / 최신순

**개인화 추천**

- 사용자 선호 태그 기반 HNSW 벡터 유사도 검색
- 기본 15건, 확장 50건 (`?extended=true`)

### 6-3. 영상 스트리밍 파이프라인

```
Admin 업로드 → Presigned URL → MinIO 직접 PUT
                ↓
        Kafka: VideoTranscodeRequestedEvent
                ↓
        transcoder-worker: HLS 변환
                ↓
        VideoFile.hlsPath 업데이트 → Video.status = PUBLISHED
```

**재생 URL 발급**: `GET /api/play/video/{videoId}` → MinIO Presigned GET URL 반환

### 6-4. 결제 & 구독

- 요금제: **SUB_BASIC** (4,900원 / 30일)
- **멱등성 보장**: Idempotency-Key 헤더 필수 (Redis SETNX 기반 — [상세 참조](#8-결제-멱등성))
- 구독 상태: ACTIVE → CANCELED (만료 전 해지 예약) → EXPIRED

### 6-5. 조회수 처리

```
정상:  Redis dedup(쿨타임) → Redis 누적 → 3분 배치 Flush → DB
Redis 다운: DB 직접 반영 (dedup 없이)
Flush 실패: 다음 배치 재시도 (TTL 만료 전까지 Redis 유지)
```

### 6-6. 관리자 기능 (Admin API)

- 콘텐츠 목록/상세 조회, 메타데이터 수정
- 영상 Draft 생성 → Presigned URL 발급 → 업로드 확인 → Kafka 발행
- 사용자 목록/상세 조회 (로그인 방식, 구독 상태 포함)

---

## 7. 장애 복원력 아키텍처

> **3차 스프린트 핵심 작업**: 외부 의존성(Redis / ES / OAuth)이 장애 상황에서도
> 서비스가 완전히 중단되지 않도록 Fallback 및 명확한 에러 응답을 구현했습니다.

### 7-1. Redis 장애 Fallback

| 기능 | Redis 정상 | Redis 장애 시 |
|------|-----------|--------------|
| 이메일 OTP 발송 | 쿨다운 체크 → Redis 저장 | 쿨다운 체크 스킵(SMTP rate limit 대체) → 저장 실패 시 503 |
| 이메일 OTP 검증 | Redis GET | Redis 연결 실패 → 503 |
| OTP 삭제 (1회용) | Redis DEL | 실패해도 TTL 자동 만료 → 계속 진행 |
| 조회수 증가 | Redis INCR (dedup 포함) | DB 직접 INCR (dedup 생략) |
| 조회수 쿨타임 초기화 | Redis DEL | 실패해도 TTL 자동 만료 → 무시 |
| 결제 멱등성 | SETNX + 결과 캐시 | 503 반환 (멱등성 보장 불가 → 결제 진행 안 함) |

**구현 포인트**: `RedisConnectionFailureException` catch → 비즈니스 로직별 적절한 Fallback 분기

```java
// ViewCountService — Redis 완전 다운 시 DB 직접 반영
try {
    Boolean result = redisTemplate.opsForValue().setIfAbsent(historyKey, "1", dynamicTtl);
    isFirstView = Boolean.TRUE.equals(result);
} catch (RedisConnectionFailureException e) {
    redisAvailable = false;
    isFirstView = true; // Redis 상태 모를 때는 첫 조회로 간주
}
if (!redisAvailable) {
    videoRepository.incrementViewCount(videoId, 1L);   // DB Fallback
    contentRepository.incrementViewCount(contentId, 1L);
}
```

### 7-2. OAuth 외부 API 장애 처리

소셜 로그인 시 외부 OAuth API(Google/Kakao/Naver) 연결 실패는 `OAuthLoginException`으로 처리되어 **502 Bad Gateway**로 응답합니다.

```
POST /api/auth/login/google
  ↓ (Google API 타임아웃 / 오류)
OAuthLoginException
  ↓
GlobalExceptionHandler → HTTP 502 (외부 서비스 장애 명시)
```

### 7-3. Elasticsearch 장애 Fallback

**검색 API** (`GET /api/search`)

```
ES 정상: Nori + N-gram + kNN 하이브리드 검색
  ↓ DataAccessException (ES 다운)
DB LIKE 검색 Fallback
  - keyword: title LIKE '%{keyword}%'
  - category / genre / tag 필터 동일 적용
  - 인기순 / 최신순 정렬 (pageable.sort 기반 판별)
  ↓ DB도 실패
빈 결과([]) 반환 (500 대신 graceful degradation)
```

**자동완성 API**: ES 다운 시 → 빈 배열 `[]` 반환 (자동완성 미동작이 500보다 UX 자연스러움)

**실시간 인덱스 동기화 스케줄러** (`ContentRealtimeSyncScheduler`)

```
30초마다 updatedAt > lastSyncedAt 콘텐츠 조회 → ES 인덱싱
  ↓ ES 다운 (DataAccessException / Exception)
  - 해당 콘텐츠만 건너뛰고 다음 항목 계속 처리 (스케줄러 지속 실행)
  - 보수 전략: 1건이라도 실패 시 lastSyncedAt 유지 → 다음 주기 재시도
```

### 7-4. HTTP 상태 코드 매핑 (GlobalExceptionHandler)

| 예외 | HTTP | 의미 |
|------|------|------|
| `OAuthLoginException` | 502 | 외부 소셜 API 장애 |
| `RedisConnectionFailureException` | 503 | Redis 직접 연결 실패 |
| `RedisServiceUnavailableException` | 503 | Redis 장애로 서비스 불가 |
| `DataAccessResourceFailureException` | 503 | ES/DB 외부 저장소 연결 실패 |
| `PaymentIdempotencyException` | 400 | Idempotency-Key 헤더 누락 |
| `PaymentInProgressException` | 409 | 동일 키 처리 중 (재시도 가능) |
| `ConflictException` | 409 | 요청 충돌 |
| `IllegalArgumentException` | 400 | 잘못된 입력값 |
| `JwtTokenExpiredException` | 401 | 토큰 만료 |
| `JwtInvalidTokenException` | 401 | 유효하지 않은 토큰 |

---

## 8. 결제 멱등성

> 브랜치: `feat/#148-payment-idempotency`

클라이언트 네트워크 재시도, UI 버그 등으로 인한 **중복 결제를 원천 차단**합니다.

### 동작 흐름

```
POST /api/payments/subscribe
  Header: Idempotency-Key: {UUID}   ← 클라이언트가 매 요청마다 UUID 생성
  Body: { "paymentProvider": "CARD" }

Step 1. Idempotency-Key 누락
  → 400 Bad Request (PaymentIdempotencyException)

Step 2. Redis SETNX("payment:idempotency:{userId}:{key}", "PROCESSING", TTL=30s)
  ├── SETNX 자체 실패 (Redis 장애) → 503 (멱등성 보장 불가)
  ├── 키 없음 → 락 획득 성공 → 결제 처리 진행
  └── 키 존재 (SETNX false):
      ├── 값 = "PROCESSING"          → 409 (동일 요청 처리 중, 잠시 후 재시도)
      ├── 값 = JSON + hash 일치      → 200 (캐시된 결과 반환, 중복 요청)
      ├── 값 = JSON + hash 불일치    → 409 (같은 키로 다른 내용 요청)
      └── 파싱 실패                  → 409 (안전 차단, 결제 진입 금지)

Step 3. 결제 처리
  ├── 성공 → Redis SET("...", {requestHash, response}, TTL=24h)   [PROCESSING 교체]
  └── 실패 → Redis DEL (다음 요청이 동일 키로 재시도 가능)
```

### 레이스 컨디션 방지

```
❌ 이전 (GET → SET 패턴): 동시 요청 시 두 요청이 모두 키 없음을 확인 → 중복 결제
✅ 수정 후 (SETNX): 원자적 SET-if-Not-eXists → 둘 중 하나만 락 획득 보장
```

### 예외 설계

| 예외 | 의미 | HTTP |
|------|------|------|
| `PaymentIdempotencyException` | Idempotency-Key 헤더 누락 (클라이언트 버그) | 400 |
| `PaymentInProgressException` | 동일 키 처리 중 또는 파싱 불가 (재시도 대상) | 409 |
| `ConflictException` | 동일 키로 다른 내용의 요청 (새 키 필요) | 409 |
| `RedisServiceUnavailableException` | Redis 장애 → 멱등성 보장 불가 | 503 |

---

## 9. API 엔드포인트 요약

### User API (`/api`)

#### 인증 (`/api/auth`)
```
POST   /signup/email/send-code          인증코드 발송 (Public)
POST   /signup/email/verify-code        인증코드 검증 → Setup Token (Public)
POST   /signup/profile/nickname         닉네임 설정 (Setup Token)
POST   /signup/profile/tags             선호 태그 선택 → 가입 완료 (Setup Token)
POST   /login/email                     이메일 로그인 (Public)
POST   /login/google                    Google OAuth (Public)
POST   /login/kakao                     Kakao OAuth (Public)
POST   /login/naver                     Naver OAuth (Public)
POST   /reissue                         Access Token 재발급 (Public)
POST   /logout                          로그아웃 (JWT)
```

#### 콘텐츠 (`/api/contents`)
```
GET    /home/watching-list              시청 중 콘텐츠 5건 (JWT)
GET    /home/default-list               기본 탐색 목록 (Public)
GET    /home/bookmark-list              최근 북마크 5건 (JWT)
GET    /home/trending                   트렌딩 10건 (Public)
GET    /{contentId}                     콘텐츠 상세 (Public)
GET    /{contentId}/episodes-list       에피소드 목록 (Public)
GET    /recommended                     개인화 추천 15건 (JWT)
GET    /recommended?extended=true       개인화 추천 50건 (JWT)
```

#### 검색 (`/api/search`)
```
GET    /search?keyword=&tag=&genre=&category=&sort=   전문 검색 (Public)
POST   /index/rebuild                                  인덱스 재구축
```

#### 결제 & 구독 (`/api/payments`, `/api/membership`)
```
POST   /payments/subscribe              구독 결제 (JWT + Idempotency-Key 필수)
GET    /membership/me                   구독 상태 조회 (JWT)
POST   /membership/cancel               구독 해지 예약 (JWT)
GET    /membership/uplus-verification   U+ 인증 상태 (JWT)
POST   /membership/uplus-verification   U+ 멤버십 인증 (JWT)
```

#### 북마크 (`/api/users/me/bookmarks`)
```
POST   /{contentId}      북마크 추가 (JWT)
DELETE /{contentId}      북마크 삭제 (JWT)
GET    /                 북마크 목록 (JWT, 페이지네이션)
GET    /playlist         타입별 그룹 조회 (JWT)
```

#### 기타
```
POST   /histories/watch-point           시청 위치 저장 (JWT)
GET    /histories/watch-histories       시청 이력 조회 (JWT)
POST   /comments/                       댓글 작성 (JWT)
PATCH  /comments/{commentId}            댓글 수정 (JWT)
DELETE /comments/{commentId}            댓글 삭제 (JWT)
GET    /comments/video/{videoId}        댓글 목록 (Public)
GET    /play/video/{videoId}            영상 재생 URL (Public)
PATCH  /profile/image                   프로필 이미지 수정 (JWT)
GET    /profile/image/presigned-url     프로필 이미지 업로드 URL (JWT)
GET    /users/me                        현재 사용자 정보 (JWT)
PUT    /users/me/preferred-tags         선호 태그 갱신 (JWT)
```

### Admin API (`/admin`)
```
POST   /login                           관리자 로그인
GET    /contents/list                   콘텐츠 목록 (페이지네이션)
GET    /contents/{contentId}            콘텐츠 상세
PUT    /contents/{contentId}/metadata   메타데이터 수정
POST   /videos/draft                    영상 Draft 생성
POST   /videos/presign                  Presigned URL 발급
POST   /videos/confirm                  업로드 확인 → 트랜스코딩 발행
GET    /users/list                      사용자 목록
GET    /users/{userId}                  사용자 상세 (로그인 방식, 구독 포함)
```

---

## 10. 개발 환경 설정

### 사전 요구사항

- Java 21+
- Docker & Docker Compose
- Gradle (Wrapper 포함, 별도 설치 불필요)

### 빠른 시작

```bash
# 1. 환경 변수 파일 생성
cp .env.example .env
# .env 파일을 열어 필요한 값 입력 (11번 섹션 참고)

# 2. 인프라 + 서비스 전체 기동
docker compose up -d

# 3. 로컬 개발 (인프라만 Docker, 서비스는 IDE에서 실행)
docker compose up -d mysql redis kafka elasticsearch minio

# 4. user-api 단독 실행 (로컬 개발용)
./gradlew :modules:user-api:bootRun
```

### Flyway 마이그레이션

user-api 기동 시 자동 실행됩니다.

```
modules/user-api/src/main/resources/db/migration/
├── V1__init.sql                          초기 스키마
├── V2602152235__add_indexes_and_tags.sql 인덱스 + 태그 시드
├── V2602160605__add_content_data.sql     샘플 콘텐츠 시드
├── V2602260306__add_payment_fields.sql   결제 필드 추가
├── V2603011807__add_admin_account.sql    관리자 계정 삽입
└── V2603040936__dumpData_telecomMembers.sql U+ 회원 데이터
```

### Swagger UI

기동 후 아래 URL에서 API 문서를 확인할 수 있습니다.

```
http://localhost:8080/swagger-ui.html   (user-api)
http://localhost:8081/swagger-ui.html   (admin-api)
```

---

## 11. 환경 변수

`.env` 파일을 프로젝트 루트에 생성합니다.

```env
# MySQL
MYSQL_ROOT_PASSWORD=
MYSQL_DATABASE=
MYSQL_USER=
MYSQL_PASSWORD=
MYSQL_PORT=3306

# Redis
REDIS_PORT=6379

# Kafka
KAFKA_PORT=9094

# Elasticsearch
ES_PORT=9200
ES_SECURITY_ENABLED=false
ELASTIC_PASSWORD=
ES_HEAP=512m

# MinIO
MINIO_PORT=9000
MINIO_CONSOLE_PORT=9001
MINIO_ROOT_USER=
MINIO_ROOT_PASSWORD=

# JWT
JWT_SECRET=
JWT_ACCESS_EXPIRATION_MS=1800000
JWT_REFRESH_EXPIRATION_MS=1209600000

# OAuth2 — Google
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GOOGLE_REDIRECT_URI=

# OAuth2 — Kakao
KAKAO_CLIENT_ID=
KAKAO_CLIENT_SECRET=
KAKAO_REDIRECT_URI=

# OAuth2 — Naver
NAVER_CLIENT_ID=
NAVER_CLIENT_SECRET=
NAVER_REDIRECT_URI=

# Gmail SMTP
MAIL_USERNAME=
MAIL_PASSWORD=

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:3000

# API Ports
USER_API_PORT=8080
ADMIN_API_PORT=8081
```

---
