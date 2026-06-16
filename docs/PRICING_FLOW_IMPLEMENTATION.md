# 가격 산정 로직 구현 기록

> 작성일: 2026-06-15  
> 작업자: 상준 (dx)  
> 대상: beyond404-server PR 리뷰 및 적용 담당자 (본체)

---

## 1. 무슨 작업을 했는가

고객이 가전을 촬영하면 GPT-4o가 모델명을 인식하고, 그 모델명으로 DB에서 실제 제품 스펙을 조회해서
스크랩 가치와 크레딧을 계산하도록 연결했다.

기존에는 모든 계산이 코드 안에 하드코딩된 "중형" + 목(mock) 모델명으로 돌아가고 있었다.

---

## 2. 전체 플로우

```
[고객 앱]
    ↓ 사진 2장 업로드 (외관 + 라벨)
[analyzePhoto API]
    → applyMockInspection() 실행
    → 이 시점에서는 size_grade = "중형" (하드코딩), 크레딧도 중형 기준
    → 화면에 "예상 크레딧" 표시

[고객이 GPT-4o 인식 결과 확인/수정 후 "맞아요" 버튼 클릭]
    ↓ 모델명 전달
[updateAppliance API]  ← ★ 이번 작업의 핵심
    → appliance_specs 테이블에서 모델명 조회 (대소문자 무관)
    → 조회 성공: 실제 size_grade, 실제 size_metric 사용
    → 조회 실패: 기존 "중형" 유지 (graceful fallback, 오류 없음)
    → 실제 스크랩 가치 재계산
    → 크레딧 업데이트

[고객이 신제품 선택]
    ↓ 상품 ID + 가격 전달
[selectReplacementProduct API]  ← ★ 이번 작업
    → 가격으로 제품 등급 자동 계산 (프론트가 보낸 grade는 무시)
         · 150만원 이상  → 프리미엄
         · 50만원 이상   → 일반
         · 50만원 미만   → 보급형
    → creditRateFor(등급, 교환횟수) × 신제품가격 → 추가 크레딧
    → 최종 크레딧 = 스크랩 가치 + min(신제품가 × 크레딧비율, 신제품가 × 15%)
```

---

## 3. 변경/추가된 파일

### 새로 추가된 파일

| 파일 | 역할 |
|------|------|
| `src/main/java/com/swapit/domain/entity/ApplianceSpecEntity.java` | appliance_specs 테이블 JPA 엔티티 |
| `src/main/java/com/swapit/repository/ApplianceSpecsRepository.java` | 모델명으로 스펙 조회하는 repository |
| `src/main/resources/db/migration/V7__seed_market_products.sql` | 5개 LG 제품 market_products 테이블 적재 |

### 수정된 파일

**`SwapRequestService.java`**
- `ApplianceSpecsRepository` 주입 추가
- `updateAppliance()`: 모델명으로 appliance_specs 조회 → state에 실제 size_grade/metric 전달
- `selectReplacementProduct()`: 프론트의 grade 대신 `gradeFromPrice(price)` 결과 사용
- `gradeFromPrice()` 헬퍼 메서드 추가

**`SwapRequestState.java`**
- `updateAppliance()` 시그니처 변경: 기존 5개 파라미터 → 7개 (`dbSizeGrade`, `dbSizeMetric` 추가)
- DB에서 size_grade가 오면 반영, null이면 기존 값 유지

---

## 4. DB 관련 (Flyway Migration)

현재 RDS에 적용된 버전: V1 ~ V4  
이번 PR에 추가된 버전: **V5, V6, V7**

| 버전 | 내용 |
|------|------|
| V5 | `appliance_specs` 테이블 생성 |
| V6 | 566개 LG 제품 스펙 데이터 적재 (냉장고 200, TV 202, 세탁기 119, 에어컨 11, 전자레인지 34) |
| V7 | `market_products` 테이블에 5개 신제품 적재 |

서버 재시작 시 Flyway가 V5→V6→V7 순서로 자동 실행됨.

---

## 5. size_grade 기준표

| 제품 | 소형 | 중형 | 대형 |
|------|------|------|------|
| 냉장고 | ~300L 미만 | 300~600L | 600L 초과 |
| 세탁기 | ~55kg 미만 (기계 무게) | 55~80kg | 80kg 초과 |
| 에어컨 | ~25kg 미만 | 25~35kg | 35kg 초과 |
| 전자레인지 | ~20L 미만 | 20~30L | 30L 초과 |
| TV | ~43인치 미만 | 43~65인치 | 65인치 초과 |

size_grade에 따라 `scrapValueFor()` 메서드가 스크랩 가치를 반환한다. (`SwapRequestState.java` 737번째 줄 참고)

---

## 6. 주의사항

1. **GPT-4o 모델명 포맷**: DB에는 소문자 `gr-b247squu` 형태로 저장됨. 조회는 대소문자 무관하게 동작하나, 하이픈 누락이나 suffix 차이(`-stand` 등)가 있으면 조회 실패 → "중형" fallback. 운영 중 매칭률이 낮으면 모델명 정규화 로직 추가 필요.

2. **applyMockInspection은 건드리지 않음**: 사진 업로드 단계에서는 아직 GPT-4o 결과를 DB 조회에 연결하지 않았다. `analyzePhoto`는 여전히 mock. 실제 GPT-4o 결과가 `updateAppliance`로 넘어오는 구조가 완성되면 그게 핵심 연결 지점.

3. **market_products는 아직 서버에서 직접 조회 안 함**: `selectReplacementProduct`는 프론트가 보낸 가격을 그대로 신뢰해서 등급 계산. 추후 보안 강화 시 productId로 DB 조회해서 가격 검증하는 로직 필요.

---

---

## 7. 본체에게

안녕, 상준이야.

이번에 내가 작업한 내용 요약하면:

**서버에서 해줘야 할 것**

1. `git pull` (main 브랜치)
2. 서버 재시작 → Flyway가 V5/V6/V7 자동 실행함
3. 재시작 후 `appliance_specs` 테이블에 566행, `market_products`에 5행 들어가있는지 확인

```bash
# EC2에서 확인용
psql $DB_URL -c "SELECT appliance_type, COUNT(*) FROM appliance_specs GROUP BY appliance_type;"
psql $DB_URL -c "SELECT product_name, price FROM market_products;"
```

**코드 충돌 가능성**

- `SwapRequestService.java`: `selectReplacementProduct` 메서드 수정됨. 네가 이 메서드 건드린 게 있으면 충돌 날 수 있음
- `SwapRequestState.java`: `updateAppliance` 파라미터 2개 추가됨 (7번째, 8번째 파라미터). 이걸 호출하는 다른 코드가 있으면 컴파일 에러 남

**구조적으로 바꾼 것**

기존: 모든 size_grade = 하드코딩 "중형"  
변경: GPT-4o 모델명 → appliance_specs DB 조회 → 실제 size_grade 사용

나머지 비즈니스 로직(`scrapValueFor`, `creditRateFor`, `calculateEstimatedFinalCredit`)은 네가 짠 거 그대로 타고 들어감. 건드린 거 없어.

궁금한 거 있으면 디스코드로 연락해.
