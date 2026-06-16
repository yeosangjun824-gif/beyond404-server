# LG SwapIt 고객용 앱 API 명세서

이 문서는 고객용 ThinQ 앱 화면과 백엔드가 어떤 API로 소통할지 정리한 문서입니다.

현재 백엔드는 데모용 Mock API 일부만 구현되어 있습니다. 따라서 각 API는 아래처럼 구분합니다.

- `현재 구현`: 지금 코드에 이미 있는 API
- `MVP 구현 예정`: 고객용 앱 데모 완성에 필요하지만 아직 백엔드에 없는 API
- `외부 연동 예정`: Google Maps, VLM 모델처럼 외부 서비스를 붙일 때 필요한 API

## 1. 기본 규칙

### Base URL

로컬 개발:

```txt
http://localhost:8080
```

같은 와이파이 모바일 테스트:

```txt
http://{PC_IP}:8080
```

배포 후:

```txt
https://{backend-domain}
```

### 요청/응답 형식

- 요청은 기본적으로 JSON을 사용합니다.
- 응답도 기본적으로 JSON을 사용합니다.
- 이미지 파일은 직접 JSON에 담지 않고, 나중에 S3 업로드 URL 방식으로 처리하는 것을 권장합니다.

### 주요 ID

| 이름 | 의미 |
| --- | --- |
| `userId` | 고객을 구분하는 ID |
| `swapRequestId` | 고객이 만든 교환/수거 신청 ID |
| `applianceId` | 수거 대상 가전 ID |
| `bookingId` | 예약 ID |
| `crewId` | 수거 기사/크루 ID |
| `creditId` | 발급된 크레딧 ID |
| `productId` | LG 마켓 상품 ID |

### 공통 에러 응답

```json
{
  "code": "SWAP_REQUEST_NOT_FOUND",
  "message": "교환 신청을 찾을 수 없습니다.",
  "detail": "swapRequestId=1"
}
```

## 2. 고객용 앱 전체 흐름

```txt
1. 고객이 SwapIt 기능 시작
2. 교환 신청 생성
3. 가전 종류 선택
4. 카메라로 가전 촬영
5. 사진 업로드 및 VLM 분석
6. 고객이 인식된 가전 정보 확인/수정
7. 예상 보상가 확인
8. 수거 예약 또는 바로 콜
9. 수거 크루 매칭 및 트래킹
10. 수거 완료 후 최종 검수
11. 최종 감정가 확인
12. 크레딧 발급
13. LG 마켓에서 크레딧 사용
```

## 3. 현재 구현된 API

현재 백엔드 코드에 이미 있는 API입니다.

### 3.1 교환 신청 생성

```http
POST /api/swap-requests
```

고객이 SwapIt 신청을 처음 시작할 때 사용합니다.

#### Request

```json
{
  "userName": "Demo User",
  "phoneNumber": "+91-90000-00000"
}
```

#### Response

```json
{
  "id": 1,
  "status": "CREATED",
  "appliance": {
    "applianceType": "unknown",
    "brand": "unknown",
    "conditionGrade": "unknown",
    "uploadedFileName": null
  },
  "preValuation": {
    "minEstimatedValue": 0,
    "maxEstimatedValue": 0,
    "currency": "INR",
    "basis": []
  },
  "booking": null,
  "tracking": {
    "message": "교환 신청이 생성되었습니다.",
    "estimatedArrivalAt": "2026-06-08T16:00:00"
  },
  "credit": null,
  "recyclingReport": {
    "summary": "수거 후 검수 예정",
    "steps": []
  }
}
```

### 3.2 교환 신청 조회

```http
GET /api/swap-requests/{swapRequestId}
```

고객 앱에서 현재 신청 상태를 다시 불러올 때 사용합니다.

#### Response

```json
{
  "id": 1,
  "status": "PRE_VALUATION_READY",
  "appliance": {
    "applianceType": "washing_machine",
    "brand": "LG",
    "conditionGrade": "good",
    "uploadedFileName": "swapit-washing-machine.jpg"
  }
}
```

### 3.3 사진 분석 Mock

```http
POST /api/swap-requests/{swapRequestId}/photos
```

현재 데모에서는 실제 이미지 파일 대신 `fileName`만 보내고, 백엔드가 Mock VLM 결과와 예상 보상가를 만들어줍니다.

#### Request

```json
{
  "fileName": "swapit-washing-machine-1710000000000.jpg"
}
```

#### Response

```json
{
  "id": 1,
  "status": "PRE_VALUATION_READY",
  "appliance": {
    "applianceType": "washing_machine",
    "brand": "LG",
    "conditionGrade": "good",
    "uploadedFileName": "swapit-washing-machine-1710000000000.jpg"
  },
  "preValuation": {
    "minEstimatedValue": 1500,
    "maxEstimatedValue": 2400,
    "currency": "INR",
    "basis": [
      "제품군: LG 세탁기",
      "외관 상태: 양호",
      "사진 기반 예비 산정",
      "최종 금액은 수거 후 검수와 원자재 가치 평가로 확정"
    ]
  }
}
```

### 3.4 수거 예약 확정

```http
POST /api/swap-requests/{swapRequestId}/booking
```

고객이 수거 날짜, 시간, 주소를 확정할 때 사용합니다.

#### Request

```json
{
  "bookingDate": "2026-06-12",
  "bookingTime": "10:00",
  "address": "A-12, New Delhi demo street"
}
```

#### Response

```json
{
  "id": 1,
  "status": "BOOKING_CONFIRMED",
  "booking": {
    "bookingDate": "2026-06-12",
    "bookingTime": "10:00",
    "address": "A-12, New Delhi demo street"
  }
}
```

### 3.5 최종 검수 Mock 완료

```http
POST /api/swap-requests/{swapRequestId}/final-valuation/mock
```

데모에서 최종 검수가 끝난 것처럼 처리하고 크레딧을 발급할 때 사용합니다.

#### Response

```json
{
  "id": 1,
  "status": "CREDIT_ISSUED",
  "credit": {
    "amount": 1900,
    "currency": "INR",
    "status": "ISSUED"
  }
}
```

## 4. 고객용 MVP 구현 예정 API

아래 API는 현재 화면 흐름을 백엔드 중심으로 바꾸기 위해 필요한 API입니다.

### 4.1 가전 정보 확인/수정

```http
PATCH /api/swap-requests/{swapRequestId}/appliance
```

VLM이 인식한 가전 정보가 틀렸을 때 고객이 직접 수정한 정보를 저장합니다.

#### Request

```json
{
  "applianceType": "washing_machine",
  "brand": "LG",
  "modelName": "FHP1411Z9P",
  "estimatedAge": "1~3년",
  "conditionGrade": "파손 있음"
}
```

#### Response

```json
{
  "id": 1,
  "status": "INSPECTION_CONFIRMED",
  "appliance": {
    "applianceType": "washing_machine",
    "brand": "LG",
    "modelName": "FHP1411Z9P",
    "estimatedAge": "1~3년",
    "conditionGrade": "파손 있음"
  }
}
```

### 4.2 예상 보상가 계산

```http
POST /api/swap-requests/{swapRequestId}/pre-valuation
```

고객이 확인한 가전 정보를 기준으로 예상 보상가 범위를 계산합니다.

#### Response

```json
{
  "swapRequestId": 1,
  "status": "PRE_VALUATION_READY",
  "preValuation": {
    "minEstimatedValue": 1500,
    "maxEstimatedValue": 2400,
    "currency": "INR",
    "basis": [
      "LG 세탁기 제품군 기준",
      "사진상 외관 파손 가능성 반영",
      "수거 후 원자재 가치에 따라 최종 금액 변동 가능"
    ],
    "disclaimer": "사진 기반 금액은 예상 범위이며, 최종 크레딧은 수거 후 검수 결과로 확정됩니다."
  }
}
```

### 4.3 예상 보상가 확인

```http
POST /api/swap-requests/{swapRequestId}/pre-valuation/accept
```

고객이 예상 보상가 범위를 확인하고 다음 단계로 넘어갈 때 사용합니다.

#### Response

```json
{
  "swapRequestId": 1,
  "status": "PRE_VALUATION_ACCEPTED"
}
```

### 4.4 예약 가능 날짜 조회

```http
GET /api/bookings/available-dates?region=New%20Delhi
```

예약 가능한 날짜 목록을 보여줄 때 사용합니다.

#### Response

```json
{
  "dates": [
    "2026-06-12",
    "2026-06-13",
    "2026-06-14"
  ]
}
```

### 4.5 예약 가능 시간 조회

```http
GET /api/bookings/available-times?date=2026-06-12&region=New%20Delhi
```

선택한 날짜에서 예약 가능한 시간 목록을 보여줄 때 사용합니다.

#### Response

```json
{
  "date": "2026-06-12",
  "times": [
    "10:00",
    "13:00",
    "17:30"
  ]
}
```

### 4.6 예약 변경

```http
PATCH /api/bookings/{bookingId}
```

예약 완료 후 고객이 날짜, 시간, 주소를 변경할 때 사용합니다.

#### Request

```json
{
  "bookingDate": "2026-06-13",
  "bookingTime": "13:00",
  "address": "A-12, New Delhi demo street"
}
```

### 4.7 예약 취소

```http
DELETE /api/bookings/{bookingId}
```

예약 완료 후 고객이 수거 예약을 취소할 때 사용합니다.

## 5. 바로 콜 / 기사 호출 API

바로 콜은 고객 앱과 크루 앱이 같은 백엔드를 통해 연결되어야 합니다.

### 5.1 바로 콜 요청 생성

```http
POST /api/swap-requests/{swapRequestId}/pickup-call
```

고객이 “근처 수거 크루 호출하기”를 눌렀을 때 사용합니다.

#### Request

```json
{
  "pickupLat": 28.6139,
  "pickupLng": 77.209,
  "pickupAddress": "A-12, New Delhi demo street",
  "detailAddress": "2nd floor",
  "applianceType": "washing_machine"
}
```

#### Response

```json
{
  "callId": 10,
  "swapRequestId": 1,
  "status": "SEARCHING_CREW",
  "message": "근처 LG 인증 수거 파트너를 찾고 있습니다."
}
```

### 5.2 매칭 상태 조회

```http
GET /api/swap-requests/{swapRequestId}/pickup-call
```

고객 앱에서 “기사 찾는 중”, “매칭 성공” 상태를 확인할 때 사용합니다.

#### Response

```json
{
  "callId": 10,
  "status": "CREW_ASSIGNED",
  "crew": {
    "crewId": 7,
    "name": "Raj Kumar",
    "phone": "+91-90000-11111",
    "certificationStatus": "LG_CERTIFIED"
  },
  "estimatedMinutes": 12
}
```

## 6. 트래킹 API

지도 위에서 기사 위치와 고객 위치를 보여주기 위한 API입니다.

### 6.1 고객용 트래킹 조회

```http
GET /api/swap-requests/{swapRequestId}/tracking
```

고객 앱에서 기사 위치, 도착 예상 시간, 진행 상태를 조회할 때 사용합니다.

#### Response

```json
{
  "swapRequestId": 1,
  "status": "DRIVER_ON_THE_WAY",
  "statusMessage": "기사님이 이동 중이에요",
  "pickupLocation": {
    "lat": 28.6139,
    "lng": 77.209,
    "address": "A-12, New Delhi demo street"
  },
  "crewLocation": {
    "lat": 28.62,
    "lng": 77.21,
    "heading": 120,
    "speed": 24,
    "updatedAt": "2026-06-08T16:00:00"
  },
  "estimatedMinutes": 12,
  "crew": {
    "crewId": 7,
    "name": "Raj Kumar",
    "phone": "+91-90000-11111",
    "certificationStatus": "LG_CERTIFIED"
  }
}
```

### 6.2 트래킹 상태 문구

| status | 고객에게 보여줄 문구 |
| --- | --- |
| `REQUESTED` | 수거 신청이 완료되었어요 |
| `CREW_ASSIGNED` | 기사님이 배정되었어요 |
| `DRIVER_ON_THE_WAY` | 기사님이 이동 중이에요 |
| `NEARBY` | 기사님이 곧 도착해요 |
| `ARRIVED` | 기사님이 도착했어요 |
| `PICKUP_COMPLETED` | 수거가 완료되었어요 |

## 7. Google Maps 연동 예정 API

Google Maps API 키를 프론트에 직접 노출하지 않으려면, 백엔드에서 일부 기능을 감싸는 방식이 좋습니다.

### 7.1 주소 자동완성

```http
GET /api/maps/address-suggestions?keyword=A-12%20New%20Delhi
```

고객이 수거 주소를 입력할 때 자동완성 후보를 보여줍니다.

#### Response

```json
{
  "suggestions": [
    {
      "placeId": "google-place-id-1",
      "label": "A-12, Connaught Place, New Delhi",
      "address": "A-12, Connaught Place, New Delhi, India"
    }
  ]
}
```

### 7.2 주소를 좌표로 변환

```http
POST /api/maps/geocode
```

고객이 입력한 주소를 지도 좌표로 바꿀 때 사용합니다.

#### Request

```json
{
  "address": "A-12, New Delhi demo street"
}
```

#### Response

```json
{
  "lat": 28.6139,
  "lng": 77.209,
  "formattedAddress": "A-12, New Delhi, India"
}
```

### 7.3 도착 예상 시간 계산

```http
POST /api/maps/eta
```

크루 위치와 고객 위치를 기준으로 예상 도착 시간을 계산할 때 사용합니다.

#### Request

```json
{
  "origin": {
    "lat": 28.62,
    "lng": 77.21
  },
  "destination": {
    "lat": 28.6139,
    "lng": 77.209
  }
}
```

#### Response

```json
{
  "estimatedMinutes": 12,
  "distanceMeters": 3200
}
```

## 8. VLM 모델 연동 예정 API

VLM은 프론트에서 직접 호출하지 않고 백엔드에서 호출하는 것을 권장합니다.

이유:

- API Key를 프론트에 노출하지 않기 위해
- 이미지 저장 위치와 분석 결과를 백엔드에서 관리하기 위해
- 나중에 OpenAI, Gemini, 자체 모델 등으로 바꿔도 프론트 코드를 덜 수정하기 위해

### 8.1 이미지 업로드 URL 발급

```http
POST /api/swap-requests/{swapRequestId}/images/presigned-url
```

프론트가 S3에 이미지를 직접 업로드할 수 있도록 임시 업로드 URL을 발급합니다.

#### Request

```json
{
  "fileName": "swapit-washing-machine.jpg",
  "contentType": "image/jpeg"
}
```

#### Response

```json
{
  "uploadUrl": "https://s3-upload-url",
  "imageKey": "swap-requests/1/swapit-washing-machine.jpg",
  "expiresInSeconds": 300
}
```

### 8.2 이미지 업로드 완료 처리

```http
POST /api/swap-requests/{swapRequestId}/images/complete
```

프론트가 S3 업로드를 완료한 뒤 백엔드에 알려줍니다.

#### Request

```json
{
  "imageKey": "swap-requests/1/swapit-washing-machine.jpg"
}
```

#### Response

```json
{
  "swapRequestId": 1,
  "status": "PHOTO_UPLOADED",
  "imageUrl": "https://cdn.example.com/swap-requests/1/swapit-washing-machine.jpg"
}
```

### 8.3 VLM 분석 요청

```http
POST /api/swap-requests/{swapRequestId}/inspection
```

업로드된 가전 사진을 VLM 모델에 보내 제품군, 브랜드, 모델명, 외관 상태를 추정합니다.

#### Request

```json
{
  "imageKey": "swap-requests/1/swapit-washing-machine.jpg"
}
```

#### Response

```json
{
  "swapRequestId": 1,
  "status": "INSPECTION_COMPLETED",
  "inspection": {
    "applianceType": "washing_machine",
    "brand": "LG",
    "modelName": "FHP1411Z9P",
    "estimatedAge": "1~3년",
    "conditionGrade": "파손 있음",
    "ocrText": "FHP1411Z9P",
    "confidence": 82,
    "warnings": [
      "사진만으로 내부 부품 상태는 확인할 수 없습니다."
    ]
  }
}
```

### 8.4 VLM 재촬영 요청

```http
POST /api/swap-requests/{swapRequestId}/inspection/retake-request
```

사진이 너무 어둡거나 가전이 잘렸을 때 재촬영을 요청합니다.

#### Response

```json
{
  "swapRequestId": 1,
  "status": "RETAKE_REQUIRED",
  "reason": "가전 전체 외관이 사진에 충분히 보이지 않습니다."
}
```

## 9. 최종 검수 / 재검수 API

### 9.1 최종 검수 상태 조회

```http
GET /api/swap-requests/{swapRequestId}/final-valuation
```

수거 완료 후 최종 검수가 진행 중인지, 완료되었는지 확인합니다.

#### Response

```json
{
  "swapRequestId": 1,
  "status": "FINAL_VALUATION_READY",
  "finalValuation": {
    "amount": 1900,
    "currency": "INR",
    "reasons": [
      "전면 사용 흔적은 있으나 주요 파손은 확인되지 않았습니다.",
      "일부 내부 부품은 재사용 가능성이 있어 보상가에 반영했습니다.",
      "수거, 분류, 안전 해체 비용을 차감했습니다."
    ]
  }
}
```

### 9.2 재검수 요청

```http
POST /api/swap-requests/{swapRequestId}/re-review
```

고객이 최종 감정가에 대해 한 번만 재검수를 요청할 때 사용합니다.

#### Request

```json
{
  "reason": "외관 상태가 실제보다 낮게 평가된 것 같습니다.",
  "detail": "제품 전면에는 큰 파손이 없습니다."
}
```

#### Response

```json
{
  "swapRequestId": 1,
  "status": "RE_REVIEW_REQUESTED",
  "message": "재검수 요청이 접수되었습니다."
}
```

## 10. 크레딧 API

### 10.1 크레딧 발급

```http
POST /api/swap-requests/{swapRequestId}/credits
```

최종 감정가를 ThinQ 크레딧으로 발급합니다.

#### Response

```json
{
  "creditId": 100,
  "amount": 1900,
  "currency": "INR",
  "status": "ISSUED",
  "expiresAt": "2026-12-31"
}
```

### 10.2 고객 크레딧 조회

```http
GET /api/users/{userId}/credits
```

고객이 보유한 ThinQ 크레딧 목록을 조회합니다.

## 11. LG 마켓 API

### 11.1 추천 상품 목록 조회

```http
GET /api/market/products?category=washing_machine
```

크레딧을 사용할 수 있는 LG 가전 상품 목록을 조회합니다.

#### Response

```json
{
  "products": [
    {
      "productId": 1,
      "category": "washing_machine",
      "name": "LG 11Kg Front Load Washing Machine",
      "price": 62900,
      "currency": "INR",
      "imageUrl": "https://example.com/lg-washer.jpg",
      "description": "인버터 모터와 에너지 절감 기능을 갖춘 프론트 로드 세탁기입니다.",
      "creditAppliedPrice": 61000
    }
  ]
}
```

### 11.2 상품 상세 조회

```http
GET /api/market/products/{productId}
```

고객이 추천 상품을 눌렀을 때 상세 정보를 보여줍니다.

### 11.3 주문 생성

```http
POST /api/market/orders
```

크레딧을 사용해서 상품 구매를 진행합니다.

#### Request

```json
{
  "userId": 1,
  "productId": 1,
  "creditId": 100,
  "deliveryAddress": "A-12, New Delhi demo street"
}
```

## 12. 알림 API

### 12.1 고객 알림 목록 조회

```http
GET /api/users/{userId}/notifications
```

예약 완료, 수거 완료, 최종 검수 완료, 재검수 완료 같은 알림 목록을 조회합니다.

### 12.2 알림 읽음 처리

```http
PATCH /api/notifications/{notificationId}/read
```

고객이 알림을 확인했을 때 읽음 처리합니다.

## 13. 상태값 정의

### Swap Request Status

| 상태 | 의미 |
| --- | --- |
| `CREATED` | 교환 신청이 생성됨 |
| `PHOTO_UPLOADED` | 사진 업로드 완료 |
| `INSPECTION_COMPLETED` | VLM 분석 완료 |
| `INSPECTION_CONFIRMED` | 고객이 가전 정보 확인 완료 |
| `PRE_VALUATION_READY` | 예상 보상가 준비 완료 |
| `PRE_VALUATION_ACCEPTED` | 고객이 예상 보상가 확인 완료 |
| `BOOKING_CONFIRMED` | 예약 확정 |
| `SEARCHING_CREW` | 바로 콜 기사 찾는 중 |
| `CREW_ASSIGNED` | 기사 배정 완료 |
| `PICKUP_IN_PROGRESS` | 수거 진행 중 |
| `PICKUP_COMPLETED` | 수거 완료 |
| `FINAL_INSPECTION_IN_PROGRESS` | 최종 검수 중 |
| `FINAL_VALUATION_READY` | 최종 감정가 확인 가능 |
| `RE_REVIEW_REQUESTED` | 재검수 요청 접수 |
| `RE_REVIEW_COMPLETED` | 재검수 완료 |
| `CREDIT_ISSUED` | 크레딧 발급 완료 |
| `COMPLETED` | 전체 프로세스 완료 |
| `CANCELLED` | 신청 취소 |

## 14. 우선순위

### 1순위: 현재 프론트 연결 안정화

- `POST /api/swap-requests`
- `POST /api/swap-requests/{id}/photos`
- `PATCH /api/swap-requests/{id}/appliance`
- `POST /api/swap-requests/{id}/pre-valuation`
- `POST /api/swap-requests/{id}/booking`

### 2순위: 바로 콜 / 트래킹

- `POST /api/swap-requests/{id}/pickup-call`
- `GET /api/swap-requests/{id}/pickup-call`
- `GET /api/swap-requests/{id}/tracking`

### 3순위: VLM / 이미지 저장

- `POST /api/swap-requests/{id}/images/presigned-url`
- `POST /api/swap-requests/{id}/images/complete`
- `POST /api/swap-requests/{id}/inspection`

### 4순위: 최종 검수 / 크레딧 / 마켓

- `GET /api/swap-requests/{id}/final-valuation`
- `POST /api/swap-requests/{id}/re-review`
- `POST /api/swap-requests/{id}/credits`
- `GET /api/market/products`
- `POST /api/market/orders`
