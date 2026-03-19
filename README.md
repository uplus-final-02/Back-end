# U+ OTT 플랫폼 백엔드

> 정식 콘텐츠 스트리밍과 유저 숏폼 클립을 함께 제공하는 멀티 콘텐츠 OTT 서비스

[![License](https://img.shields.io/badge/license-MIT-brightgreen)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4-brightgreen?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.4-blue?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7-red?logo=redis&logoColor=white)](https://redis.io/)
[![Elasticsearch](https://img.shields.io/badge/Elasticsearch-8-005571?logo=elasticsearch&logoColor=white)](https://www.elastic.co/)
[![Kafka](https://img.shields.io/badge/Kafka-3.7-231F20?logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)

**기간**: 2025.02.04 ~ 2025.03.19
---

## 팀 구성

| 김우식 | 서수민 | 우수정 |
| :---: | :---: | :---: |
| <img src="https://github.com/rladntlr.png" width="150" height="150"><br/>[@rladntlr](https://github.com/rladntlr) | <img src="https://github.com/s0ooooomin.png" width="150" height="150"><br/>[@s0ooooomin](https://github.com/s0ooooomin) | <img src="https://github.com/soojung122.png" width="150" height="150"><br/>[@soojung122](https://github.com/soojung122) |
| **Backend** | **Backend** | **Backend** |
| 회원/인증 · 검색/추천<br/>장애 Fallback 설계 | 콘텐츠 · 플레이어 | 인증/회원 · 백오피스 |

<br/>

|                                                         조성재                                                          | 최가은 | 최보근 | 한상옥 |
|:--------------------------------------------------------------------------------------------------------------------:| :---: | :---: | :---: |
| <img src="https://github.com/seongejae.png" width="150" height="150"><br/>[@seongejae](https://github.com/seongejae) | <img src="https://github.com/eunii2.png" width="150" height="150"><br/>[@eunii2](https://github.com/eunii2) | <img src="https://github.com/ChoiBoKeun1.png" width="150" height="150"><br/>[@ChoiBoKeun1](https://github.com/ChoiBoKeun1) | <img src="https://github.com/kay0307.png" width="150" height="150"><br/>[@kay0307](https://github.com/kay0307) |
|                                                     **Backend**                                                      | **Backend** | **Backend** | **Backend** |
|                                                      검색 고도화 <br/>마이페이지 / 사용자                                                       | 백오피스 · 콘텐츠<br/>트랜스코딩&ABR | 콘텐츠 · 플레이어<br/>실시간 인기차트 · 배포 | 마이페이지/플레이어성능<br/>테스트 시연 |

---
## 배포

| | URL |
|--|-----|
| **Frontend** | https://utopiaott.vercel.app |
| **Backend API** | https://ureca-utopia.duckdns.org |
| **Swagger** | https://ureca-utopia.duckdns.org/swagger-ui/index.html |

---

## 기술 스택

| 분류 | 기술                                                  |
|------|-----------------------------------------------------|
| Language / Framework | Java 21, Spring Boot 4, Spring Security 6           |
| Database | MySQL 8.4 + Flyway, Redis 7, Elasticsearch 8 (Nori) |
| Messaging | Apache Kafka 3.7 (KRaft)                            |
| Storage / CDN | AWS S3, AWS CloudFront, FFmpeg                      |
| Infra | Docker, Docker Compose, GitHub Actions              |

---

## 시스템 구성

```mermaid
flowchart LR
    C["Client<br/>Web / App"]
    AC["Admin Client<br/>Backoffice"]

    U["user-api<br/>Auth / Search / Recommendation / Playback / User Content"]
    A["admin-api<br/>Admin Upload / Content Management / Operations"]
    T["transcoder-worker<br/>Async Video Processing"]

    DB["MySQL<br/>Persistent Storage"]
    R["Redis<br/>Cache / OTP / Rate Limit / View Buffer"]
    ES["Elasticsearch<br/>Search / Vector Recommendation"]
    K["Kafka<br/>Transcode Event Queue"]
    OS["MinIO / AWS S3<br/>Original + HLS Storage"]
    CF["CloudFront<br/>Signed HLS Delivery"]
    F["FFmpeg<br/>MP4 → HLS"]

    C --> U
    AC --> A

    U --> DB
    U --> R
    U --> ES
    U --> K
    U --> OS
    U --> CF

    A --> DB
    A --> R
    A --> K
    A --> OS

    K --> T
    T --> F
    F --> OS
    OS --> CF

```

| 모듈                    | 역할 및 책임|주요기능|
|-----------------------|-------------------------------|------|
| `core`                | JWT·OAuth 보안, 스토리지 추상화, Kafka 이벤트 |JWT/OAuth2 보안 설정, Redis/Kafka 설정, S3/MinIO 스토리지 추상화 인터페이스, 공통 예외 처리|
| `domain`              | JPA 엔티티·리포지토리 (공유 도메인)        |JPA 엔티티 정의, Querydsl 리포지토리, 공통 서비스 로직 (사용자, 영상 메타데이터 등)|
| `user-api`            | 사용자 API 서버                    |영상 스트리밍 URL 생성(CloudFront Signed URL), 콘텐츠 검색(Elasticsearch), 캐싱 처리, 마이페이지 관리|
| `admin-api`           | 관리자 API 서버                    |신규 콘텐츠 업로드, 트랜스코딩 작업 트리거(Kafka Message 발행), 대시보드 및 통계 관리|
| `transcoder-worker`   | 비동기 영상 트랜스코딩 워커               |Kafka로부터 인코딩 메시지 수신, FFmpeg을 활용한 HLS 세그먼트 생성 및 S3 업로드|

---

## 주요 기능

### 인증
- 이메일 회원가입 4단계 멀티스텝 (Setup Token으로 서버 세션 없이 상태 전달)
- 소셜 로그인 (Google / Kakao / Naver), JWT Access(30분) + Refresh(14일) Rotation
- 로그인 5회 실패 시 Redis 기반 계정 잠금

### 콘텐츠 스트리밍
- 정식 콘텐츠(시리즈·영화): 구독자 전용 접근 제어, HLS 기반 스트리밍
- 유저 숏폼: 업로드 → Kafka → FFmpeg HLS 변환 → CloudFront 서명 URL 재생
- 에피소드별 `lastPositionSec` 저장으로 이어보기 지원

### 검색
- Elasticsearch Nori 형태소 분석 + 초성 검색 + 오타 교정 제안
- Redis 캐싱 자동완성 (TTL 24h)

### 추천
- **정식 콘텐츠**: 유저 선호 태그 기반 100차원 벡터 → ES kNN 후보 추출 → 태그유사도(60%) + 인기도(25%) + 신선도(15%) 내부 랭킹
- **숏폼 피드**: 마지막 시청 클립의 tagVector를 다음 쿼리 벡터로 재사용하는 seedId 기반 무한 스크롤
- ES 장애 시 DB 인기순 자동 폴백

### 기타
- 조회수 Redis Sorted Set 버퍼링 + ShedLock 배치 플러시
- 구독 결제 Redis 멱등성 키로 중복 결제 방지
- LG U+ 멤버십 전화번호 인증 연동

---

## 로컬 실행

```bash
# 1. 저장소 클론
git clone https://github.com/uplus-final-02/Back-end.git
cd Back-end

# 2. 인프라 실행 (MySQL, Redis, Kafka, Elasticsearch, MinIO)
docker compose up -d

# 3. 환경변수 설정
cp .env.example .env
# .env 파일에 JWT_SECRET, S3, OAuth 키 등 입력

# 4. 빌드 및 실행
./gradlew :modules:user-api:bootRun
```

> API 문서: http://localhost:8080/swagger-ui/index.html

---

## 트러블슈팅

<details>
<summary><b>김우식</b></summary>

<br>

**멀티스텝 회원가입 — Setup Token**

- **문제**: 4단계 회원가입 중간 상태를 저장할 방법이 필요. 임시 DB 테이블 방식은 중도 이탈 유저 데이터가 쌓이고 서버 수평 확장 시 세션 공유 문제 발생
- **해결**: JWT 클레임에 단계별 상태를 누적하는 Setup Token 도입. 서버는 아무것도 저장하지 않고 마지막 단계에서 한 번에 계정 생성 → Stateless 수평 확장 가능

**Refresh Token 동시성 — Redis SETNX 분산 락**

- **문제**: 동일 Refresh Token으로 요청 2개가 동시에 들어오면 둘 다 유효로 판단해 토큰 2개 발급 — 보안 취약점
- **해결**: DB 조회 전 Redis SETNX로 선착순 1개 요청만 통과, 나머지 즉시 반환 → 레이스 컨디션 원천 차단

**FETCH JOIN + Pageable N+1 — 2-step 쿼리**

- **문제**: 컬렉션 JOIN + Pageable 조합 시 JPA가 DB LIMIT 대신 인메모리 LIMIT 적용 → 대용량에서 OOM 위험
- **해결**: 1단계에서 ID만 조회(DB LIMIT 정확 적용) → 2단계에서 해당 ID만 FETCH JOIN

</details>

<details>
<summary><b>서수민</b></summary>

<br>

## 🧪 더미 데이터 생성 (Dummy Data Generation)

### 1. 문제 상황
- 한국영상자료원, TMDB 등 다양한 영화/드라마 관련 API를 검토했으나  
  **프로젝트 DB 스키마에 정확히 맞는 데이터 API가 존재하지 않음**
- 일부 데이터는 배우, 줄거리, 출시연도 등의 값이 누락되어 있어  
  **서비스에 바로 활용하기 어려운 문제 발생**

---

### 2. 해결 방안

#### 2-1. 데이터 소스 선정 및 가공
- 여러 API를 비교 및 테스트한 결과, **TMDB API**를 최종 선정
- TMDB의 다양한 엔드포인트를 활용하여  
  **프로젝트 DB 구조에 맞게 데이터 가공 및 재구성**

#### 2-2. 데이터 정제 (Filtering)
- 다음 기준을 통해 데이터 품질 확보
    - 주요 컬럼(배우, 줄거리, 출시연도 등)이 `NULL`인 데이터는 제외
    - 별점 평가 인원 **5명 이상**인 콘텐츠만 사용
    - **18세 미만 이용 가능 콘텐츠**만 필터링

#### 2-3. 데이터 구조 매핑
- 세부 설명(description)은 JSON 형태로 저장하여 **유연성 확보**
- TMDB의 **인기도(popularity)**를 기반으로 조회수 데이터 생성
- 장르 데이터는 `tags` 테이블로 분리 후:
    - 콘텐츠별 `content_tags` 매핑 데이터 생성
    - `user_preferred_tags`는 priority=1인 태그 중 랜덤 매핑

---

### 3. 결과
- `contents` 테이블: 약 **16,000건**
    - 제목, 설명, 썸네일 등 주요 정보 포함
- `videos` 테이블: 약 **17,000건**
    - 시리즈의 에피소드 데이터 포함

👉 이를 통해 **검색 / 추천 시스템 / UI 기능을 실제 데이터 기반으로 테스트 가능**

</details>

<details>
<summary><b>우수정</b></summary>

<br>

# 🔥 트러블슈팅: 실시간 JOIN 기반 통계 집계의 성능 저하 및 데이터 불일치 문제

## 1. 문제 상황 (Problem)

- 쿼리 복잡도 증가: 태그 기반 홈 통계를 실시간 JOIN으로 계산하면서 `contents`, `content_tags`, `watch_histories`, `content_metric_snapshots` 등 다수 테이블이 결합되어 쿼리가 과도하게 복잡해졌습니다.
- 성능 저하 및 DB 부하: 조회 시마다 집계를 수행하는 구조로 인해 트래픽 증가 시 응답 속도 저하 및 DB 부하가 급증했습니다.
- 통계 일관성 문제: 동일 요청이라도 조회 시점에 따라 집계 기준이 달라져 통계 값이 변동되는 문제가 발생했습니다.
- 데이터 정합성 리스크: 삭제된 시청 이력이나 비활성 콘텐츠가 포함될 가능성이 있어 신뢰성 있는 통계 제공이 어려웠습니다.

---

## 2. 해결 과정 (Action)

실시간 집계 구조의 한계를 해결하기 위해 스냅샷 기반 사전 집계 아키텍처로 전환했습니다.

### [Backend] 실시간 집계 제거 및 기준 시점 정합성 확보
- `content_metric_snapshots`에서 기준 시점 이전 최신 bucket 데이터만 조회하도록 쿼리 구조 재설계
- `watch_histories`에 `deleted_at IS NULL`, `created_at < 기준 시각` 조건을 명시하여 데이터 정합성 확보

### [Batch] 태그 단위 사전 집계 및 UPSERT 구조 도입
- 태그 기준 집계 결과를 `tag_home_stats` 테이블에 저장하는 배치 처리 로직 구축
- 중복 실행 및 데이터 갱신을 고려하여 UPSERT 방식으로 설계

### [API] 조회 구조 단순화
- 기존의 실시간 집계 쿼리를 제거하고 사전 계산된 결과 조회 방식으로 API 변경
- 조회 시 추가 연산 없이 즉시 응답 가능하도록 구조 개선

---

## 3. 결과 (Result)

- 복잡한 JOIN 기반 실시간 집계를 제거하여 조회 성능 대폭 개선
- 동일 기준 시점 기반 집계로 통계 데이터 일관성 확보
- DB 부하 감소로 트래픽 증가 상황에서도 안정적인 서비스 운영 가능
- 배치 + 스냅샷 구조를 통해 확장 가능한 통계 아키텍처 확보

</details>

<details>
<summary><b>조성재</b></summary>

<br>

## 🔥 Troubleshooting: Elasticsearch 하이브리드 검색 랭킹 역전 및 결과 일관성 확보

<details>
<summary><strong>문제 상황</strong></summary>

### 1) 스코어링 알고리즘 한계
Elasticsearch의 기본 스코어링(BM25/TF-IDF) 특성상,  
문서 길이가 길고 특정 단어가 많이 반복된 필드가 더 높은 점수를 받는 경우가 있었습니다.

### 2) 랭킹 역전 현상
사용자가 특정 콘텐츠의 **제목**을 정확히 검색했음에도,  
해당 검색어가 **설명(description)** 필드에 여러 번 포함된 다른 콘텐츠가 더 높은 `_score`를 받아  
상단에 노출되는 문제가 발생했습니다.

즉, 검색 엔진의 계산 방식과 실제 사용자 검색 의도 사이에 불일치가 있었습니다.

### 3) 결과 일관성 부족
동일한 `_score`를 가진 문서들이 존재할 경우,  
조회할 때마다 결과 순서가 달라지는 문제가 있었습니다.

이로 인해 같은 검색어를 반복 입력해도 결과 순서가 흔들려  
사용자 입장에서 검색 신뢰도가 떨어지는 문제가 발생했습니다.

</details>

<details>
<summary><strong>원인 분석</strong></summary>

기존 구조에서는 검색 정확도를 Elasticsearch의 기본 스코어링에 크게 의존하고 있었습니다.

- `title`과 `description`의 중요도를 충분히 분리하지 못함
- 설명 필드의 높은 term frequency(TF)가 제목 일치보다 더 강하게 반영됨
- 동일 점수 문서에 대한 명확한 후순위 정렬 기준(Tie-breaker)이 없음

그 결과,
- **제목 정확 일치보다 설명 반복 문서가 더 위로 올라오는 랭킹 역전**
- **동점 문서 순서가 매 요청마다 달라지는 비일관성**
  이 함께 발생했습니다.

</details>

<details>
<summary><strong>해결 방법</strong></summary>

## 1. Multi-field 기반 매핑 재설계
단일 필드에서 여러 검색 요구사항을 동시에 처리하지 않고,  
`title` 필드를 목적별로 분리해 다중 분석기를 적용했습니다.

- **형태소 분석용**: Nori analyzer
- **자동완성용**: N-gram analyzer
- **초성 검색용**: Lowercase 기반 analyzer

이를 통해 하나의 필드에 서로 다른 검색 목적이 충돌하지 않도록 분리하고,  
정확 검색 / 자동완성 / 초성 검색을 각각 독립적으로 처리할 수 있도록 구조를 개선했습니다.

## 2. 제목 필드 Boosting 강화
`multi_match` 쿼리 구성 시 필드 가중치를 명시적으로 조정했습니다.

- `title^100`
- `description^1`

설명 필드에 검색어가 여러 번 반복되더라도,  
제목 매칭이 훨씬 더 높은 우선순위를 가지도록 설정하여  
사용자의 검색 의도와 실제 랭킹이 일치하도록 튜닝했습니다.

## 3. Tie-breaker 다중 정렬 기준 도입
기존에는 페이징 위주로 처리되던 쿼리를 개선하여  
명시적인 다중 정렬 기준을 적용했습니다.

정렬 우선순위는 다음과 같습니다.

1. **검색 정확도** (`_score`)
2. **인기순** (`totalViewCount`)
3. **최신순** (`id` DESC)

이를 통해 `_score`가 동일한 문서가 있더라도  
항상 동일한 기준으로 결과가 정렬되도록 보장했습니다.

</details>

<details>
<summary><strong>적용 결과</strong></summary>

- 제목 정확 일치보다 설명 반복 문서가 상단에 노출되던 **랭킹 역전 현상 해결**
- 사용자 검색 의도에 맞는 콘텐츠가 최상단에 노출되도록 **검색 품질 개선**
- 동일 점수 문서 간 순서가 흔들리지 않도록 **결과 일관성 확보**
- 검색 정확도와 사용자 신뢰도를 모두 강화한 **안정적인 하이브리드 검색 구조** 완성

</details>

</details>

<details>
<summary><b>최가은</b></summary>

<br>

## 1) 트랜스코딩이 왜 필요한가에서 시작

- 목표: 업로드된 원본 MP4를 **HLS(m3u8 + ts)** 로 변환해 스트리밍 가능하게 만들고, 네트워크 상황에 따라 자동 품질 전환(ABR)을 지원
- 결정: FFmpeg 기반 HLS 트랜스코딩 파이프라인 구축, 저장소는 S3/MinIO 사용

### 핵심 교훈
- “파일 업로드 → 변환 → 재생 가능 상태로 전환”을 동기 요청으로 처리하면 요청이 너무 길어져 서버가 불안정해집니다.  
  → **비동기 파이프라인이 필요합니다.**

---

## 2) 첫 구현: 동기 처리의 한계를 체감

### 겪은 문제
- API 요청에서 FFmpeg를 직접 실행하면:
    - 요청 타임아웃
    - 서버 스레드 점유로 전체 서비스 영향
    - 트랜스코딩 중 서버 재시작 시 작업 유실

### 정리
- 트랜스코딩은 CPU/IO-heavy 작업이라 “웹 API 서버”가 하면 안 되고,
- 작업 서버(Worker)로 분리해야 운영이 가능하다는 결론을 내렸습니다.

---

## 3) Worker 분리 + Kafka: 처음 써본 사건

### 도입 배경
- “업로드 요청”과 “트랜스코딩 실행”을 분리하기 위해 메시지 큐가 필요했습니다.
- Kafka를 선택한 이유(문서에 쓰기 좋음):
    - 비동기 작업 분리, 재시도/확장 용이
    - consumer group 기반 수평 확장 가능
    - 이벤트 기반으로 admin/user 각각 다른 흐름 구성 가능

### 처음 부딪히는 포인트
- 토픽 네이밍/환경변수 매핑 실수로 consumer가 메시지를 못 먹는 문제
- group-id가 달라야 할 곳/같아야 할 곳을 헷갈림
- “ACK가 찍혔는데 왜 DB 반영이 안 되지?” 같은 **비동기 특유의 디버깅 난이도**

### 교훈
- 비동기 파이프라인에서 “성공”은
    - 워커 내부 성공 로그가 아니라,
    - **결과 이벤트 소비 + DB 반영 + 클라이언트 통지까지 끝난 상태**를 의미합니다.

---

## 4) 이벤트 스키마/라우팅 설계: admin vs user 분기

### 진행 내용
- requestType(HLS_ADMIN/HLS_USER) 기반으로 결과 토픽을 분리(또는 라우팅)
- VideoTranscodeResultEvent에 최소 필드(식별자/상태/hlsKey/duration/reason) 정의

### 대표 트러블슈팅 포인트
- 결과 이벤트에 contentId, videoFileId가 제대로 들어오지 않으면
    - consumer가 DB 업데이트를 못 해서 “DONE인데도 DB는 PENDING” 상태가 됩니다.

### 교훈
- 이벤트 스키마는 “지금 필요한 것”뿐만 아니라  
  **나중에 운영/디버깅에 필요한 정보까지 포함해야** 안정적입니다.

---

## 5) 대규모 트랜스코딩(큐 적체)에서 터진 운영 이슈

### 상황(문서용)
- 관리자 요청이 한 번에 다수 쌓이며 워커가 연속 트랜스코딩 수행
- 로컬에서는 빠른데 배포 서버(c5.large)에서는 시간이 늘어나고, 결국 프로세스가 죽는 문제가 발생

### 원인 후보(운영에서 자주 발생하는 패턴)
- CPU 부족: ABR 다중 해상도 HLS는 CPU를 많이 사용
- 메모리 부족: FFmpeg + Java 힙 + 버퍼 → OOM 가능
- 디스크(/tmp) 폭증: 세그먼트 파일 생성량이 많아 디스크 부족 가능
- 동시 실행 수 과다: max-concurrent-ffmpeg가 서버 스펙 대비 높음

### 해결 방향
- 동시 실행 제한(세마포어) + 큐 적체 대비
- 서버 스펙 업/워커 수평 확장
- 트랜스코딩 작업 디렉토리 cleanup 보장

### 교훈
- “기능 구현” 단계와 “운영 가능한 설계”는 다릅니다.  
  → **리소스 가드레일(동시성 제한/모니터링/정리 작업)** 이 반드시 필요합니다.

---

## 6) 상태 전이 정책(콘텐츠 공개/비공개) 정합성 문제

### 문제
- 콘텐츠는 ACTIVE로 바뀌었는데 실제 재생 단위(Video/VideoFile)는 PRIVATE로 남는 등 정합성 불일치
- 시리즈(SERIES)는 에피소드 단위로 PUBLIC 여부가 달라야 하는데, 단건 정책을 그대로 쓰면 어긋남

### 정책 정리(문서용)
- DONE이 오면 무조건 공개가 아니라:
    - **“사용자가 공개를 원했던 항목만”** PUBLIC 전환
- 콘텐츠 전체 상태는:
    - public episode가 하나라도 있으면 ACTIVE
    - 없으면 HIDDEN
- HIDDEN으로 내리면 모든 episode를 PRIVATE로 내려 일관성 유지

### 교훈
- 도메인 규칙(정책)을 코드로 명확히 만들지 않으면  
  “상태가 여러 테이블에 흩어진 시스템”은 쉽게 불일치가 발생합니다.

</details>

<details>
<summary><b>최보근</b></summary>

<br>

# 트러블슈팅: 이기종 도메인 간 CloudFront Signed Cookie 세팅 및 CORS 차단 우회

## 1. 문제 상황 (Problem)

* **보안 정책 충돌:** 영상 보안을 위해 CloudFront Signed Cookie를 도입했으나, API 서버(DuckDNS 무료 도메인)와 CDN 서버(.cloudfront.net) 간의 도메인 불일치로 인해 브라우저의 교차 출처(Cross-Domain) 보안 정책이 발동되었습니다.
* **쿠키 저장 실패:** 퍼블릭 서픽스(Public Suffix)로 취급되는 `.cloudfront.net` 특성상, 백엔드에서 강제로 `Set-Cookie` 헤더를 내려주어도 브라우저가 서드파티 쿠키로 간주하여 저장을 차단했습니다.
* **증상:** 프론트엔드에서 쿠키를 동봉하지 못해 동영상 요청(HLS `.m3u8`) 시 CloudFront CORS 정책 위반 및 403 Forbidden 에러가 발생했습니다.

---

## 2. 해결 과정 (Action)

고비용의 커스텀 도메인 구매 없이 문제를 근본적으로 해결하기 위해, 백엔드-인프라-프론트엔드의 역할을 분리하여 퍼스트 파티(First-Party) 쿠키 발급 플로우를 구축했습니다.

### [Backend] 쿠키 직접 주입 폐기 및 DTO 전달
* 컨트롤러에서 직접 `Set-Cookie`를 내려주던 방식을 폐기했습니다.
* 서명된 3가지 데이터(Policy, Signature, KeyPairId)를 생성하여 JSON DTO에 담아 프론트엔드로 전달하도록 변경했습니다.

### [Infra] CloudFront Edge Function 도입
* CloudFront 내부에 쿼리스트링으로 전달받은 텍스트 데이터를 실제 쿠키로 변환해 주는 전용 라우터(`/set-cookie`)를 CloudFront Functions로 구축했습니다.
* 503 에러 방지를 위해 대소문자 예외 처리 및 cookies 객체 매핑을 적용했습니다.

### [Frontend] 2-Step 재생 플로우 및 쿠키 동봉 세팅
* **Step 1:** 백엔드 `/play` API를 호출하여 영상 URL과 서명 교환권(DTO)을 획득합니다.
* **Step 2:** 해당 교환권을 쿼리스트링에 담아 CloudFront의 `/set-cookie` 엔드포인트를 호출합니다. 요청 타겟이 CloudFront 도메인과 일치하므로 브라우저가 정상적인 퍼스트 파티 쿠키로 저장합니다.
* **Step 3:** HLS 플레이어(`hls.js`) 설정에 `withCredentials: true`를 적용하여, 영상 조각 요청 시 정상 저장된 쿠키가 자동으로 동봉되도록 처리했습니다.

---

## 3. 결과 (Result)

* 도메인 불일치로 인한 브라우저의 서드파티 쿠키 차단 이슈와 CORS/403 에러를 해결했습니다.
* 커스텀 도메인 구매 등 추가적인 인프라 비용 지출 없이, AWS Edge 환경을 활용해 안전한 Signed Cookie 기반의 미디어 스트리밍 아키텍처를 구현했습니다.

</details>

<details>
<summary><b>한상옥</b></summary>

<br>

> 작성 예정

</details>

---

## 향후 개선 계획

### Technical Improvements

| 항목 | 내용 |
|------|------|
| **DRM 적용** | 영상 불법 복제 방지 및 저작권 보호 — Widevine, FairPlay 등 멀티 DRM 솔루션 연동 |
| **데이터 서버 이중화** | Master-Slave 구성으로 고가용성(HA) 확보, Read/Write 분리로 DB 부하 분산 |
| **ML 기반 고도화** | 개인화 추천 알고리즘 정확도 향상, 영상 자동 자막 생성 및 콘텐츠 태그 자동 분류 |

### Operational Expansion

| 항목 | 내용 |
|------|------|
| **Platform 확장** | Mobile Native 환경 구축, iOS / Android 네이티브 앱 접근성 강화, 크로스 플랫폼 UX 최적화 |
| **Business Model** | 광고 및 수익화 모델 기반 생태계 구축, 크리에이터 수익 배분 시스템, 구독제 다양화 (Basic / Standard / Premium) |
| **User Experience** | 소셜 기능 확대 (친구 맺기, 팔로우, DM), 라이브 스트리밍 및 실시간 채팅 도입, 사용자 친화적 구독 시스템 개선 |