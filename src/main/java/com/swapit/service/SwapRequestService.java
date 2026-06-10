package com.swapit.service;

import com.swapit.domain.SwapRequestState;
import com.swapit.domain.entity.ApplianceEntity;
import com.swapit.domain.entity.ApplianceImageEntity;
import com.swapit.domain.entity.SwapRequestEntity;
import com.swapit.domain.entity.UserEntity;
import com.swapit.domain.entity.ValuationEntity;
import com.swapit.domain.enums.SwapRequestStatus;
import com.swapit.dto.BookingRequest;
import com.swapit.dto.CrewCompletePickupRequest;
import com.swapit.dto.CrewLocationRequest;
import com.swapit.dto.CreateSwapRequestRequest;
import com.swapit.dto.FinalValuationRequest;
import com.swapit.dto.InstantCallRequest;
import com.swapit.dto.PhotoUploadRequest;
import com.swapit.dto.ReReviewRequest;
import com.swapit.dto.SwapRequestResponse;
import com.swapit.dto.UpdateApplianceRequest;
import com.swapit.repository.ApplianceImageRepository;
import com.swapit.repository.ApplianceRepository;
import com.swapit.repository.SwapRequestRepository;
import com.swapit.repository.UserRepository;
import com.swapit.repository.ValuationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class SwapRequestService {
    private static final long DEMO_CUSTOMER_ID = 1L;
    private static final long DEMO_CREW_ID = 101L;

    private final UserRepository userRepository;
    private final SwapRequestRepository swapRequestRepository;
    private final ApplianceRepository applianceRepository;
    private final ApplianceImageRepository applianceImageRepository;
    private final ValuationRepository valuationRepository;

    private final AtomicLong sequence = new AtomicLong(1);
    private final Map<Long, SwapRequestState> store = new ConcurrentHashMap<>();

    @Transactional
    public SwapRequestResponse create(CreateSwapRequestRequest request) {
        SwapRequestState state = createPersistentState(request);
        store.put(state.getId(), state);
        return state.toResponse();
    }

    @Transactional
    public SwapRequestResponse analyzePhoto(long id, PhotoUploadRequest request) {
        SwapRequestState state = findState(id);
        state.applyMockInspection(request.fileName(), request.applianceType(), request.imageUrl());
        persistMockInspection(id, request);
        return state.toResponse();
    }

    public SwapRequestResponse updateAppliance(long id, UpdateApplianceRequest request) {
        SwapRequestState state = findState(id);
        state.updateAppliance(
                request.applianceType(),
                request.brand(),
                request.modelName(),
                request.estimatedAge(),
                request.exteriorCondition()
        );
        return state.toResponse();
    }

    public SwapRequestResponse acceptPreValuation(long id) {
        SwapRequestState state = findState(id);
        state.acceptPreValuation();
        return state.toResponse();
    }

    public SwapRequestResponse confirmBooking(long id, BookingRequest request) {
        SwapRequestState state = findState(id);
        state.confirmBooking(
                request.bookingDate(),
                request.bookingTime(),
                request.address(),
                request.detailAddress(),
                request.pickupLat(),
                request.pickupLng()
        );
        return state.toResponse();
    }

    public SwapRequestResponse requestInstantCall(long id, InstantCallRequest request) {
        SwapRequestState state = findState(id);
        state.requestInstantCall(request.address(), request.detailAddress(), request.pickupLat(), request.pickupLng());
        return state.toResponse();
    }

    public SwapRequestResponse completeMockFinalValuation(long id) {
        SwapRequestState state = findState(id);
        state.completeMockFinalValuation();
        return state.toResponse();
    }

    public SwapRequestResponse requestReReview(long id, ReReviewRequest request) {
        SwapRequestState state = findState(id);
        state.requestReReview(request.reason());
        return state.toResponse();
    }

    public SwapRequestResponse completeMockReReview(long id) {
        SwapRequestState state = findState(id);
        state.completeReReview();
        return state.toResponse();
    }

    public SwapRequestResponse issueCredit(long id) {
        SwapRequestState state = findState(id);
        state.issueCredit();
        return state.toResponse();
    }

    public SwapRequestResponse get(long id) {
        return findState(id).toResponse();
    }

    public SwapRequestResponse getTracking(long id) {
        return findState(id).toResponse();
    }

    public List<SwapRequestResponse> getAll() {
        return store.values().stream()
                .sorted(Comparator.comparingLong(SwapRequestState::getId))
                .map(SwapRequestState::toResponse)
                .toList();
    }

    public List<SwapRequestResponse> getAvailableCalls() {
        return store.values().stream()
                .filter(state -> state.getPickupRequestId() != null)
                .filter(state -> {
                    String status = state.getPickupStatus();
                    return "REQUESTED".equals(status) || "CONFIRMED".equals(status);
                })
                .sorted(Comparator.comparingLong(SwapRequestState::getId))
                .map(SwapRequestState::toResponse)
                .toList();
    }

    public SwapRequestResponse acceptCall(long pickupRequestId) {
        SwapRequestState state = findByPickupRequestId(pickupRequestId);
        state.acceptByCrew(DEMO_CREW_ID, "Aarav Sharma");
        return state.toResponse();
    }

    public SwapRequestResponse depart(long pickupRequestId) {
        SwapRequestState state = findByPickupRequestId(pickupRequestId);
        state.departCrew();
        return state.toResponse();
    }

    public SwapRequestResponse updateLocation(long pickupRequestId, CrewLocationRequest request) {
        SwapRequestState state = findByPickupRequestId(pickupRequestId);
        state.updateCrewLocation(
                request.lat(),
                request.lng(),
                request.heading() == null ? 0.0 : request.heading(),
                request.speed() == null ? 0.0 : request.speed()
        );
        return state.toResponse();
    }

    public SwapRequestResponse arrive(long pickupRequestId) {
        SwapRequestState state = findByPickupRequestId(pickupRequestId);
        state.arriveCrew();
        return state.toResponse();
    }

    public SwapRequestResponse completePickup(long pickupRequestId, CrewCompletePickupRequest request) {
        SwapRequestState state = findByPickupRequestId(pickupRequestId);
        state.completePickup(request.pickupPhotoFileName(), request.inspectionMemo());
        return state.toResponse();
    }

    public SwapRequestResponse adminCompleteFinalValuation(long id, FinalValuationRequest request) {
        SwapRequestState state = findState(id);
        state.completeFinalValuation(
                request.amount(),
                List.of(
                        valueOrDefault(request.exteriorReason(), "?筌? ??⑤객臾???筌먦끉逾???곕????덈펲."),
                        valueOrDefault(request.partsReason(), "?遊붋????亦???띠럾???쒑땻???筌먦끉逾???곕????덈펲."),
                        valueOrDefault(request.materialReason(), "????????????띠럾????띠럾??곸궡瑗? ?꾩룇瑗????곕????덈펲."),
                        valueOrDefault(request.processingReason(), "???삵깴?? ???깆쓧 ??怨댄뜢 ???????꾩룇瑗????곕????덈펲.")
                )
        );
        return state.toResponse();
    }

    public List<SwapRequestResponse.Notification> getNotifications(long userId) {
        return store.values().stream()
                .filter(state -> state.toResponse().customerId() == userId || userId == DEMO_CREW_ID)
                .flatMap(state -> state.toResponse().notifications().stream())
                .toList();
    }

    private void persistMockInspection(long id, PhotoUploadRequest request) {
        SwapRequestEntity swapRequest = swapRequestRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Swap request not found in DB: " + id));
        String applianceType = valueOrDefault(request.applianceType(), swapRequest.getApplianceType());
        String modelName = mockModelName(applianceType);

        ApplianceEntity appliance = applianceRepository.findBySwapRequest_Id(id)
                .orElseGet(() -> applianceRepository.save(ApplianceEntity.create(swapRequest, applianceType)));
        appliance.applyMockInspection(applianceType, "LG", modelName, "1~3년", "사용 흔적 있음");
        applianceRepository.save(appliance);

        applianceImageRepository.save(ApplianceImageEntity.customerCapture(
                swapRequest,
                appliance,
                request.fileName(),
                request.imageUrl()
        ));

        valuationRepository.save(ValuationEntity.preValuation(
                swapRequest,
                1500,
                2400,
                "사진 기반 Mock VLM 분석 결과로 산정된 예상 보상가입니다."
        ));
        swapRequest.changeStatus(SwapRequestStatus.PRE_VALUATION_READY.name());
        swapRequestRepository.save(swapRequest);
    }

    private static String mockModelName(String applianceType) {
        return switch (valueOrDefault(applianceType, "washing_machine")) {
            case "refrigerator" -> "GL-T422VPZX";
            case "air_conditioner" -> "US-Q19BNZE3";
            case "tv" -> "OLED55C4";
            case "microwave" -> "MH8265DIS";
            default -> "FHP1411Z9P";
        };
    }
    private SwapRequestState createPersistentState(CreateSwapRequestRequest request) {
        String applianceType = valueOrDefault(request.applianceType(), "washing_machine");

        UserEntity user = findOrCreateUser(request);
        UserEntity savedUser = userRepository.save(user);

        SwapRequestEntity swapRequest = SwapRequestEntity.create(
                savedUser,
                applianceType,
                SwapRequestStatus.CREATED.name()
        );
        SwapRequestEntity savedSwapRequest = swapRequestRepository.save(swapRequest);
        applianceRepository.save(ApplianceEntity.create(savedSwapRequest, applianceType));

        return new SwapRequestState(savedSwapRequest.getId(), savedUser.getId(), applianceType);
    }

    private UserEntity findOrCreateUser(CreateSwapRequestRequest request) {
        if (request.userId() != null) {
            UserEntity user = userRepository.findById(request.userId())
                    .orElseThrow(() -> new NoSuchElementException("User not found: " + request.userId()));
            user.updateProfile(request.userName(), request.phoneNumber());
            return user;
        }

        String thinqUserKey = UserService.toThinqUserKey(request.phoneNumber(), request.userName());
        return userRepository.findByThinqUserKey(thinqUserKey)
                .map(existingUser -> {
                    existingUser.updateProfile(request.userName(), request.phoneNumber());
                    return existingUser;
                })
                .orElseGet(() -> UserEntity.create(thinqUserKey, request.userName(), request.phoneNumber()));
    }
    private SwapRequestState findState(long id) {
        SwapRequestState state = store.get(id);
        if (state == null) {
            throw new NoSuchElementException("Swap request not found: " + id);
        }
        return state;
    }

    private SwapRequestState findByPickupRequestId(long pickupRequestId) {
        return store.values().stream()
                .filter(state -> state.getPickupRequestId() != null && state.getPickupRequestId() == pickupRequestId)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Pickup request not found: " + pickupRequestId));
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
