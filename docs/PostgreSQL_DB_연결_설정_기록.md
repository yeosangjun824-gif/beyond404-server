# PostgreSQL DB 연결 설정 기록

## 작업 목적
- PostgreSQL 설치 이후 Spring Boot 백엔드가 로컬 PostgreSQL DB에 연결되는지 먼저 확인했다.
- 아직 테이블 생성이나 Entity 전환은 하지 않고, DB 접속 설정만 분리했다.

## 대화에서 정한 방향
- DB 테이블을 만들기 전에 백엔드가 PostgreSQL에 정상 접속되는지 먼저 확인하기로 했다.
- 기본 실행은 기존 H2/mock 흐름을 유지하고, `local` 프로필을 켰을 때만 PostgreSQL을 사용하도록 구성했다.
- GitHub push는 사용자가 명시적으로 요청할 때만 진행하기로 했다.

## 적용 내용
- `src/main/resources/application-local.yml` 파일 추가
- 로컬 PostgreSQL 접속 정보 설정
  - DB: `swapit`
  - User: `swapit_user`
  - Password: 환경변수 `SWAPIT_DB_PASSWORD` 또는 기본 개발값
- JPA `ddl-auto`는 `none`으로 유지
  - 이유: 아직 DB 테이블 생성 방식을 Flyway로 갈지 확정하기 전이며, 임의 자동 생성으로 설계와 어긋나는 것을 막기 위함

## 검증 결과
- `local` 프로필로 Spring Boot 서버를 실행했다.
- `/actuator/health` 응답이 `200 OK`로 확인됐다.
- 실행 로그에서 PostgreSQL `Database version: 17.10`이 확인되어 H2가 아니라 PostgreSQL 연결임을 확인했다.

## 다음 작업 후보
1. Flyway 의존성 추가
2. `V1__create_initial_schema.sql` 작성
3. 12개 DB 객체 기준 테이블 생성
4. 핵심 Entity/Repository 작성
5. Mock API 일부를 DB 저장 방식으로 전환

## Flyway 테이블 생성 작업
- `build.gradle`에 Flyway 의존성을 추가했다.
  - `org.flywaydb:flyway-core`
  - `org.flywaydb:flyway-database-postgresql`
- 기본 `application.yml`에서는 Flyway를 비활성화했다.
  - 이유: 팀원이 DB 없이 H2/mock 서버를 실행할 수 있게 유지하기 위함
- `application-local.yml`에서는 Flyway를 활성화했다.
  - 이유: 로컬 PostgreSQL 개발 환경에서만 실제 테이블을 자동 생성하기 위함
- `src/main/resources/db/migration/V1__create_initial_schema.sql`을 추가했다.

## 생성된 테이블
- users
- swap_requests
- appliances
- appliance_images
- valuations
- pickup_requests
- tracking_events
- re_reviews
- credits
- market_products
- pickup_result_reports
- notifications

## 검증 결과
- Spring Boot를 `local` 프로필로 실행했다.
- Flyway migration version `1`이 성공 처리됐다.
- PostgreSQL `swapit` DB에 서비스 테이블 12개와 `flyway_schema_history` 테이블이 생성된 것을 확인했다.

## POST /api/swap-requests DB 저장 연결
- `UserEntity`, `SwapRequestEntity`, `ApplianceEntity`를 추가했다.
- `UserRepository`, `SwapRequestRepository`, `ApplianceRepository`를 추가했다.
- `POST /api/swap-requests` 생성 API가 아래 테이블에 실제 데이터를 저장하도록 변경했다.
  - users
  - swap_requests
  - appliances
- 기존 프론트 응답 형식을 유지하기 위해 DB 저장 후 기존 `SwapRequestState` 응답 객체를 생성해 반환한다.

## 검증 결과
- local 프로필 서버를 실행했다.
- `POST /api/swap-requests` 테스트 요청을 보냈다.
- PostgreSQL에서 다음 데이터 적재를 확인했다.
  - `users`: `DB테스트고객`, `010-4040-2404`
  - `swap_requests`: `washing_machine`, `CREATED`, `THINQ_APP`
  - `appliances`: `washing_machine`, `LG`, `Unknown`

## 사진 촬영 / Mock VLM DB 저장 연결
- `ApplianceImageEntity`, `ValuationEntity`를 추가했다.
- `ApplianceImageRepository`, `ValuationRepository`를 추가했다.
- `POST /api/swap-requests/{id}/photos` 호출 시 아래 테이블에 실제 데이터를 저장하도록 변경했다.
  - appliance_images: 촬영 파일명, 이미지 URL, 신청 ID
  - valuations: Mock VLM 기반 예상 보상가 범위
  - appliances: Mock VLM이 인식한 브랜드, 모델명, 연식, 외관 상태
  - swap_requests: 상태를 `PRE_VALUATION_READY`로 변경

## 검증 결과
- local 프로필 서버를 실행했다.
- 교환 신청 생성 후 사진 분석 API를 호출했다.
- PostgreSQL에서 다음 데이터 적재를 확인했다.
  - `appliance_images`: `demo-aircon-capture.png`
  - `valuations`: `PRE`, `READY`, `1500~2400 INR`
  - `appliances`: `air_conditioner`, `LG`, `US-Q19BNZE3`, `1~3년`, `사용 흔적 있음`
  - `swap_requests`: `PRE_VALUATION_READY`

## 데모 로그인 API와 사용자 기반 신청 저장 연결
- 데모용 로그인 API를 추가했다.
  - `POST /api/auth/demo-login`
- 로그인 요청 시 아래 테이블에 사용자 정보를 저장하거나 기존 사용자를 다시 조회한다.
  - `users`
- 고객용 프론트에서 데모 로그인 성공 후 받은 `userId`를 저장하고, 교환 신청 생성 시 함께 전달하도록 연결했다.
- `POST /api/swap-requests`는 전달받은 `userId`를 기준으로 교환 신청을 저장한다.
  - `swap_requests.user_id`
  - `appliances.swap_request_id`

## 검증 결과
- local 프로필 서버를 새 코드로 재실행했다.
- `POST /api/auth/demo-login` 호출 결과 `userId`가 반환되는 것을 확인했다.
- 반환된 `userId`로 `POST /api/swap-requests`를 호출했다.
- PostgreSQL에서 다음 데이터 적재를 확인했다.
  - `users`: `DB Login Test`, `010-7777-8888`
  - `swap_requests`: `user_id = 2`, `washing_machine`, `CREATED`
  - `appliances`: 생성된 교환 신청과 연결된 가전 기본 정보
- 이제 데모 로그인한 고객이 교환 신청을 만들면 DBeaver에서 사용자와 신청 데이터가 연결된 상태로 확인 가능하다.
