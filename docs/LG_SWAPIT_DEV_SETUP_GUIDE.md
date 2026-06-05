# LG SwapIt 개발 시작 전 세팅 가이드

이 문서는 팀원들이 개발을 시작하기 전에 우리가 정한 기본 구조를 쉽게 이해하기 위한 문서입니다.
개발 경험이 많지 않아도 "어떤 기술을 왜 쓰는지", "코드는 어디에 넣는지", "프론트와 백엔드가 어떻게 나뉘는지"를 빠르게 파악하는 것이 목표입니다.

## 1. 지금까지 정한 것

현재까지 정한 것은 크게 3가지입니다.

```txt
1. 기술 스택
2. repository 분리
3. 폴더 구조
```

쉽게 말하면, 아직 기능을 구현한 단계는 아니고 "어떤 도구로, 어떤 저장소에, 어떤 구조로 개발할지"를 정한 단계입니다.

## 2. 기술 스택이란?

기술 스택은 프로젝트를 만들 때 사용하는 기술들의 전체 묶음입니다.

예를 들어 우리 프로젝트에서는 화면을 만들 때 Next.js를 쓰고, 서버를 만들 때 Spring Boot를 쓰고, 데이터를 저장할 때 PostgreSQL을 씁니다.
이런 도구들을 모두 합쳐서 기술 스택이라고 부릅니다.

프레임워크는 기술 스택 안에 포함되는 개념입니다.
프레임워크는 앱의 큰 구조를 잡아주는 도구입니다.

```txt
기술 스택 = 사용하는 기술 전체
프레임워크 = 그중에서 앱 구조를 잡아주는 큰 도구
```

우리 프로젝트의 대표 프레임워크는 다음과 같습니다.

```txt
Frontend 프레임워크: Next.js
Backend 프레임워크: Spring Boot
```

## 3. 우리 프로젝트 기술 스택

```txt
Frontend: Next.js + TypeScript + Tailwind CSS + TanStack Query
Backend: Java 21 + Spring Boot + Spring Web + Spring Data JPA + Validation + Lombok + Springdoc OpenAPI + Actuator
DB: H2(local) -> PostgreSQL(deploy)
Storage: AWS S3 SDK
Deploy: AWS Amplify(front) + AWS App Runner(back)
```

각 도구는 아래처럼 이해하면 됩니다.

```txt
Next.js
- 사용자가 보는 웹앱 화면과 페이지를 만들 때 사용

TypeScript
- 코드에서 데이터 형태 오류를 줄일 때 사용

Tailwind CSS
- 버튼, 카드, 간격, 색상 같은 화면 디자인을 빠르게 만들 때 사용

TanStack Query
- 프론트에서 백엔드 API 데이터를 가져오고 관리할 때 사용
```

```txt
Java 21
- 백엔드 서버 코드를 작성할 때 사용하는 언어

Spring Boot
- Java로 API 서버를 빠르게 만들 때 사용하는 프레임워크

Spring Web
- 프론트에서 온 요청을 받고 응답을 보내는 API를 만들 때 사용

Spring Data JPA
- DB에 데이터를 저장하거나 조회할 때 사용

Validation
- 요청값이 비어 있거나 잘못된 값인지 검사할 때 사용

Lombok
- 반복되는 코드를 줄일 때 사용

Springdoc OpenAPI
- API 문서를 자동으로 만들 때 사용

Actuator
- 서버가 정상적으로 실행 중인지 확인할 때 사용
```

```txt
H2
- 내 컴퓨터에서 빠르게 테스트할 때 쓰는 개발용 DB

PostgreSQL
- 배포 후 실제 데이터를 저장할 때 쓰는 DB

AWS S3 SDK
- 사용자가 업로드한 가전 사진을 S3에 저장하고 관리할 때 사용

AWS Amplify
- 프론트엔드 웹앱을 인터넷에 배포할 때 사용

AWS App Runner
- 백엔드 Spring Boot 서버를 인터넷에 배포할 때 사용
```

## 4. repository란?

repository는 코드를 저장하는 GitHub 저장소를 뜻합니다.
줄여서 repo라고도 부릅니다.

우리 프로젝트는 프론트엔드와 백엔드를 분리해서 관리합니다.

```txt
lg-swapit-web
- 사용자가 보는 화면 코드 저장소
- Next.js 프론트엔드 코드가 들어감

lg-swapit-server
- 화면 뒤에서 데이터를 처리하는 서버 코드 저장소
- Spring Boot 백엔드 코드가 들어감
```

이렇게 나누면 프론트 작업자와 백엔드 작업자가 각각 자기 역할에 맞는 저장소에서 작업할 수 있습니다.

## 5. 전체 서비스 흐름

우리 서비스는 사용자가 오래된 가전제품을 처리하고, 나중에 최종 크레딧을 받는 흐름입니다.

```txt
사용자
-> 프론트엔드 웹앱
-> 백엔드 API 서버
-> DB / S3 / VLM API
-> 백엔드 API 서버
-> 프론트엔드 웹앱
-> 사용자
```

쉽게 말하면 다음과 같습니다.

```txt
프론트엔드
- 사용자가 보는 화면
- 사진 업로드, 예상 보상가 확인, 예약, 트래킹 화면을 보여줌

백엔드
- 화면 뒤에서 실제 일을 처리하는 서버
- 사진 저장, AI 분석 요청, 예상 보상가 계산, 예약 저장, 크레딧 확정을 처리함

DB
- 사용자 신청, 가전 정보, 예약, 견적, 크레딧 데이터를 저장함

S3
- 사용자가 올린 가전 사진 파일을 저장함

VLM API
- 사진을 분석해서 제품 종류, 브랜드, 상태 등을 추출함
```

## 6. 프론트엔드 repository 구조

프론트엔드는 사용자가 보는 화면을 만드는 곳입니다.

```txt
lg-swapit-web/
├─ src/
│  ├─ app/                  # 실제 페이지 주소와 화면 뼈대를 만드는 곳
│  ├─ components/           # 여러 화면에서 같이 쓰는 버튼, 카드, 입력창 모음
│  ├─ features/             # 앱의 주요 기능을 단계별로 나눠 넣는 곳
│  │  ├─ capture/           # 사진 찍기/업로드 기능
│  │  ├─ inspection/        # AI가 사진을 분석한 결과를 보여주는 기능
│  │  ├─ pre-valuation/     # 사진 기준 예상 보상가 범위를 보여주는 기능
│  │  ├─ booking/           # 수거 날짜와 시간을 예약하는 기능
│  │  ├─ tracking/          # 수거가 어디까지 진행됐는지 보여주는 기능
│  │  ├─ final-valuation/   # 수거 후 확정된 최종 보상가를 보여주는 기능
│  │  ├─ credit/            # 최종 금액을 크레딧으로 보여주는 기능
│  │  └─ recycling/         # 내 가전이 어떻게 재활용됐는지 보여주는 기능
│  ├─ lib/                  # API 연결 코드나 자주 쓰는 함수 모음
│  ├─ types/                # 데이터 모양을 미리 정리해두는 곳
│  └─ constants/            # 상태값, 선택 옵션, 고정 문구를 모아두는 곳
├─ public/                  # 이미지, 아이콘처럼 그대로 쓰는 파일을 넣는 곳
├─ package.json             # 프론트 실행 방법과 필요한 라이브러리 목록
└─ README.md                # 프론트 실행 방법 설명
```

프론트엔드는 "사용자가 클릭하고 보는 화면"을 담당합니다.

## 7. 백엔드 repository 구조

백엔드는 화면 뒤에서 데이터를 처리하는 서버입니다.

```txt
lg-swapit-server/
├─ src/main/java/com/swapit/
│  ├─ SwapitApplication.java # 백엔드 서버를 시작하는 파일
│  ├─ controller/            # 프론트가 보낸 요청을 처음 받는 곳
│  ├─ service/               # 실제 기능 로직을 처리하는 곳
│  ├─ repository/            # DB에 데이터를 저장하거나 꺼내오는 곳
│  ├─ domain/                # DB에 저장할 핵심 데이터 구조를 정의하는 곳
│  │  └─ enums/              # 정해진 상태값을 모아두는 곳
│  ├─ dto/                   # 프론트와 주고받는 데이터 모양을 정하는 곳
│  ├─ integration/           # 외부 서비스와 연결하는 코드가 들어가는 곳
│  │  ├─ vlm/                # AI 사진 분석 API와 연결하는 곳
│  │  └─ s3/                 # 사진 저장소인 S3와 연결하는 곳
│  ├─ config/                # 서버 설정을 모아두는 곳
│  └─ common/                # 여러 곳에서 같이 쓰는 공통 코드
├─ src/main/resources/
│  ├─ application.yml        # 기본 설정 파일
│  ├─ application-local.yml  # 내 컴퓨터에서 실행할 때 쓰는 설정
│  ├─ application-prod.yml   # 배포 서버에서 실행할 때 쓰는 설정
│  └─ db/
│     └─ migration/          # DB 테이블을 자동으로 만들 때 쓰는 SQL 파일
├─ database/                 # DB 설계 공유용 문서와 SQL
├─ docs/                     # API 명세, DB 설명, 서버 흐름 문서
├─ build.gradle              # 백엔드 실행에 필요한 라이브러리 목록
├─ Dockerfile                # 백엔드를 배포용으로 포장하는 파일
└─ README.md                 # 백엔드 실행 방법 설명
```

백엔드 폴더는 아래처럼 이해하면 됩니다.

```txt
controller
- 요청 받기

service
- 실제 일 처리하기

repository
- DB랑 대화하기

domain
- 저장할 데이터 구조 만들기

dto
- 프론트와 주고받는 데이터 모양 정하기

integration
- 외부 API와 연결하기
```

## 8. 백엔드와 프론트엔드는 어떻게 연결될까?

프론트엔드는 백엔드 주소를 알고 있어야 합니다.

로컬 개발에서는 보통 이렇게 연결합니다.

```txt
프론트엔드 주소: http://localhost:3000
백엔드 주소: http://localhost:8080
```

프론트엔드에서 백엔드 API 주소를 환경변수로 관리합니다.

```txt
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

배포 후에는 이 주소가 App Runner 백엔드 주소로 바뀝니다.

```txt
NEXT_PUBLIC_API_BASE_URL=https://배포된-백엔드-url
```

## 9. MVP에서 만들 핵심 기능

2-3주 안에 만들 MVP는 너무 많은 기능을 넣기보다, 전체 흐름이 자연스럽게 작동하는 것을 목표로 합니다.

```txt
1. 가전 사진 업로드
2. Mock VLM 분석
3. 예상 보상가 범위 표시
4. 수거 날짜/시간 예약
5. 수거 진행 상태 트래킹
6. 수거 후 최종 보상가 확정
7. 크레딧 발급 상태 표시
8. 자원 순환 리포트 표시
```

중요한 점은 VLM을 처음부터 실제 API로 연결하지 않아도 된다는 것입니다.
처음에는 Mock 데이터를 사용해서 전체 흐름을 먼저 완성합니다.

```txt
Mock VLM
- 실제 AI가 아니라 가짜 분석 결과를 반환하는 코드
- 비용 없이 화면과 백엔드 흐름을 테스트할 수 있음
```

## 10. 다음 단계

이제 기술 스택, repository 분리, 폴더 구조는 정리되었습니다.
다음으로는 아래 내용을 정하면 됩니다.

```txt
1. MVP 기능 범위 확정
2. 사용자 플로우 확정
3. 상태값 정의
4. DB 테이블 초안 작성
5. API 명세 작성
6. 발표용 Mock 데이터 시나리오 작성
7. 화면별 필요한 데이터 정리
8. 개발 순서 정하기
```

가장 먼저 추천하는 작업은 사용자 플로우와 API 명세를 정하는 것입니다.
프론트와 백엔드가 어떤 데이터를 주고받을지 정해져야 실제 코드 구현이 덜 흔들립니다.

## 11. PRD 작성 목차

PRD는 Product Requirements Document의 줄임말입니다.
쉽게 말하면 "우리가 어떤 서비스를, 어떤 기능으로, 어디까지 만들 것인지 정리하는 제품 기획 문서"입니다.

팀원들이 개발에 들어가기 전에 아래 목차를 기준으로 PRD를 작성하면 됩니다.

```txt
PRD: LG SwapIt

1. 서비스 개요
   1.1 서비스 이름
   1.2 서비스 한 줄 설명
   1.3 문제 정의
   1.4 해결 방향
   1.5 핵심 가치 제안

2. 목표 및 범위
   2.1 MVP 목표
   2.2 MVP 포함 범위
   2.3 MVP 제외 범위
   2.4 성공 기준

3. 타깃 사용자
   3.1 주요 사용자
   3.2 사용자 상황
   3.3 사용자 페인포인트
   3.4 사용자 기대 행동

4. 전체 사용자 플로우
   4.1 STEP 1. 가전 사진 촬영/업로드
   4.2 STEP 2. 사진 기반 예상 보상가 범위 확인
   4.3 STEP 3. 수거 및 설치 예약
   4.4 STEP 4. 수거 크루 트래킹
   4.5 STEP 5. 최종 크레딧 확정 및 자원 순환 확인

5. 도메인별 기능 요구사항
   5.1 Swap Request
       5.1.1 교환 신청 생성
       5.1.2 신청 상태 조회
       5.1.3 신청 상태 변경
       5.1.4 신청 취소

   5.2 Appliance
       5.2.1 가전 종류 저장
       5.2.2 브랜드/모델명 저장
       5.2.3 예상 연식 저장
       5.2.4 외관 상태 저장

   5.3 Inspection
       5.3.1 이미지 업로드 URL 발급
       5.3.2 이미지 업로드 완료 처리
       5.3.3 Mock VLM 분석
       5.3.4 제품군/브랜드/상태/OCR 추출
       5.3.5 분석 결과 저장
       5.3.6 재촬영 요청

   5.4 Pre-Valuation
       5.4.1 예상 보상가 범위 계산
       5.4.2 최저/최고 금액 표시
       5.4.3 산정 근거 표시
       5.4.4 변동 가능 사유 안내
       5.4.5 예상 범위 확인

   5.5 Booking
       5.5.1 예약 가능 날짜 조회
       5.5.2 예약 가능 시간 조회
       5.5.3 수거 주소 입력
       5.5.4 예약 생성
       5.5.5 예약 변경
       5.5.6 예약 취소

   5.6 Tracking
       5.6.1 현재 진행 상태 조회
       5.6.2 상태 타임라인 표시
       5.6.3 도착 예정 시간 표시
       5.6.4 트래킹 이벤트 기록

   5.7 Final Valuation
       5.7.1 수거 후 검수 결과 등록
       5.7.2 원자재 가치 산정
       5.7.3 재사용 가능 부품 가치 산정
       5.7.4 처리 비용 반영
       5.7.5 최종 보상가 확정
       5.7.6 예비 견적과 차이 표시

   5.8 Credit
       5.8.1 최종 금액 크레딧 전환
       5.8.2 크레딧 발급
       5.8.3 크레딧 상태 조회
       5.8.4 크레딧 만료일 표시

   5.9 Recycling
       5.9.1 자원 순환 결과 생성
       5.9.2 재활용/재판매/부품화 결과 표시
       5.9.3 환경 기여 지표 표시

   5.10 Notification
       5.10.1 예약 확정 알림
       5.10.2 크루 출발 알림
       5.10.3 수거 완료 알림
       5.10.4 최종 크레딧 확정 알림

6. 화면 요구사항
   6.1 홈/시작 화면
   6.2 사진 촬영/업로드 화면
   6.3 AI 분석 중 화면
   6.4 예상 보상가 범위 화면
   6.5 예약 화면
   6.6 트래킹 화면
   6.7 최종 크레딧 확정 화면
   6.8 자원 순환 리포트 화면

7. 상태 정의
   7.1 Swap Request Status
       7.1.1 CREATED
       7.1.2 PHOTO_UPLOADED
       7.1.3 INSPECTION_COMPLETED
       7.1.4 PRE_VALUATION_READY
       7.1.5 PRE_VALUATION_ACCEPTED
       7.1.6 BOOKING_CONFIRMED
       7.1.7 CREW_ASSIGNED
       7.1.8 PICKUP_IN_PROGRESS
       7.1.9 PICKUP_COMPLETED
       7.1.10 FINAL_VALUATION_READY
       7.1.11 CREDIT_ISSUED
       7.1.12 COMPLETED
       7.1.13 CANCELLED

   7.2 Credit Status
       7.2.1 PENDING
       7.2.2 CONFIRMED
       7.2.3 ISSUED
       7.2.4 USED
       7.2.5 EXPIRED

8. 데이터 요구사항
   8.1 User
   8.2 Swap Request
   8.3 Appliance
   8.4 Inspection Result
   8.5 Pre Valuation
   8.6 Booking
   8.7 Tracking Event
   8.8 Final Valuation
   8.9 Credit
   8.10 Recycling Report

9. API 요구사항
   9.1 Swap Request API
   9.2 Inspection API
   9.3 Pre-Valuation API
   9.4 Booking API
   9.5 Tracking API
   9.6 Final Valuation API
   9.7 Credit API
   9.8 Recycling API

10. 비기능 요구사항
   10.1 성능
   10.2 보안
   10.3 개인정보 보호
   10.4 이미지 저장 정책
   10.5 장애 대응
   10.6 확장성

11. 기술 구성
   11.1 Frontend
   11.2 Backend
   11.3 Database
   11.4 Storage
   11.5 VLM Integration
   11.6 Deployment

12. MVP 제외 항목
   12.1 실제 로그인/회원가입
   12.2 실제 결제
   12.3 실시간 지도 기반 위치 추적
   12.4 실제 VLM API 필수 연동
   12.5 관리자 페이지
   12.6 WebSocket
   12.7 네이티브 앱 출시

13. 리스크 및 대응
   13.1 사진만으로 가치 판단 어려움
   13.2 최종 보상가 변동에 따른 사용자 불신
   13.3 이미지 분석 오류
   13.4 수거/해체 후 검수 지연
   13.5 VLM API 비용

14. 데모 시나리오
   14.1 냉장고 사진 업로드
   14.2 예상 보상가 범위 확인
   14.3 수거 예약
   14.4 크루 트래킹
   14.5 최종 크레딧 확정
   14.6 자원 순환 리포트 확인
```

이 목차에서 특히 중요한 부분은 아래 4가지입니다.

```txt
4. 전체 사용자 플로우
- 사용자가 앱을 어떤 순서로 쓰는지 정리하는 부분

5. 도메인별 기능 요구사항
- 실제로 어떤 기능을 만들어야 하는지 정리하는 부분

7. 상태 정의
- 백엔드가 신청 상태를 어떻게 관리할지 정리하는 부분

9. API 요구사항
- 프론트와 백엔드가 어떤 주소로 어떤 데이터를 주고받을지 정리하는 부분
```
