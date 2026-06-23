package com.swapit.service;

import com.swapit.domain.SwapRequestState;
import com.swapit.domain.entity.ApplianceEntity;
import com.swapit.domain.entity.ApplianceImageEntity;
import com.swapit.domain.entity.CrewReviewEntity;
import com.swapit.domain.entity.PickupRequestEntity;
import com.swapit.domain.entity.SwapRequestEntity;
import com.swapit.domain.entity.UserEntity;
import com.swapit.domain.entity.ValuationEntity;
import com.swapit.domain.enums.SwapRequestStatus;
import com.swapit.dto.BookingRequest;
import com.swapit.dto.BookingAvailabilityResponse;
import com.swapit.dto.ApplianceSpecLookupResponse;
import com.swapit.dto.CrewReviewRequest;
import com.swapit.dto.CrewCompletePickupRequest;
import com.swapit.dto.CrewLocationRequest;
import com.swapit.dto.CreateSwapRequestRequest;
import com.swapit.dto.FinalValuationRequest;
import com.swapit.dto.InstantCallRequest;
import com.swapit.dto.PhotoUploadRequest;
import com.swapit.dto.ReReviewRequest;
import com.swapit.dto.SelectReplacementProductRequest;
import com.swapit.dto.SwapRequestResponse;
import com.swapit.dto.UpdateApplianceRequest;
import com.swapit.domain.entity.ApplianceSpecEntity;
import com.swapit.repository.ApplianceImageRepository;
import com.swapit.repository.ApplianceRepository;
import com.swapit.repository.ApplianceSpecsRepository;
import com.swapit.repository.CrewReviewRepository;
import com.swapit.repository.PickupRequestRepository;
import com.swapit.repository.SwapRequestRepository;
import com.swapit.repository.UserRepository;
import com.swapit.repository.ValuationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
@RequiredArgsConstructor
public class SwapRequestService {
    private static final long DEMO_CUSTOMER_ID = 1L;
    private static final long DEMO_CREW_ID = 101L;
    private static final String DEMO_CREW_NAME = "무함마드";
    private static final String DEMO_CREW_PHOTO = "/crew-muhammad.png";
    private static final double DEMO_CREW_RATING = 4.9;
    private static final List<String> DEMO_CREW_REVIEW_SUMMARY = List.of(
            "깔끔하고 신속하게 수거를 진행해요",
            "약속 시간을 잘 지키고 안내가 친절해요"
    );

    private final UserRepository userRepository;
    private final SwapRequestRepository swapRequestRepository;
    private final ApplianceRepository applianceRepository;
    private final ApplianceImageRepository applianceImageRepository;
    private final ValuationRepository valuationRepository;
    private final ApplianceSpecsRepository applianceSpecsRepository;
    private final KakaoDirectionsService kakaoDirectionsService;
    private final CrewLocationProcessor crewLocationProcessor;
    private final PickupRequestRepository pickupRequestRepository;
    private final CrewReviewRepository crewReviewRepository;
    private final OpenAiVisionService openAiVisionService;
    private final CrewSettlementCalculator crewSettlementCalculator;

    private final AtomicLong sequence = new AtomicLong(1);
    private final Map<Long, SwapRequestState> store = new ConcurrentHashMap<>();
    private final Map<Long, CrewGpsState> crewGpsStore = new ConcurrentHashMap<>();
    private final Map<Long, List<SwapRequestResponse.LocationHistoryPoint>> locationHistoryStore = new ConcurrentHashMap<>();
    private final Map<Long, CachedRoute> routeCacheStore = new ConcurrentHashMap<>();
    private final List<SwapRequestResponse.LocationPoint> processingCenters = List.of(
            new SwapRequestResponse.LocationPoint("LG사이언스파크 마곡", 37.562475, 126.831166),
            new SwapRequestResponse.LocationPoint("LG전자 창원 성산 허브", 35.202531, 128.677344)
    );
    private final List<String> bookingTimeSlots = List.of(
            "09:00", "09:30", "10:00", "10:30", "11:00", "11:30",
            "12:00", "12:30", "13:00", "13:30", "14:00", "14:30",
            "15:00", "15:30", "16:00", "16:30", "17:00", "17:30", "18:00"
    );

    @PostConstruct
    void initializeDemoCrewGps() {
        resetCrewGpsStore();
    }

    @Transactional
    public SwapRequestResponse create(CreateSwapRequestRequest request) {
        SwapRequestState state = createPersistentState(request);
        store.put(state.getId(), state);
        return buildResponse(state);
    }

    @Transactional
    public SwapRequestResponse createInstantCall(com.swapit.dto.CreateInstantCallRequest request) {
        SwapRequestResponse created = create(new CreateSwapRequestRequest(
                request.userId(),
                request.userName(),
                request.phoneNumber(),
                request.applianceType()
        ));
        return requestInstantCall(created.id(), new InstantCallRequest(
                request.address(),
                request.detailAddress(),
                request.pickupLat(),
                request.pickupLng(),
                request.pickupAccuracyMeters(),
                request.pickupSource()
        ));
    }

    @Transactional
    public SwapRequestResponse analyzePhoto(long id, PhotoUploadRequest request) {
        SwapRequestState state = findState(id);
        SwapRequestEntity swapRequest = findSwapRequestEntity(id);
        String requestedApplianceType = valueOrDefault(request.applianceType(), swapRequest.getApplianceType());
        String labelImageUrl = firstKnown(request.labelImageUrl(), request.imageUrl());
        String exteriorImageUrl = firstKnown(request.exteriorImageUrl(), request.imageUrl());
        OpenAiVisionService.OpenAiVisionResult labelResult = identifyAppliance(labelImageUrl, requestedApplianceType);
        OpenAiVisionService.OpenAiConditionResult conditionResult = analyzeCondition(exteriorImageUrl, requestedApplianceType);
        String applianceType = preferKnown(labelResult.applianceType(), requestedApplianceType);
        String brand = preferKnown(labelResult.brand(), request.brand());
        String modelName = preferKnown(labelResult.modelName(), request.modelName());
        ApplianceSpecDetails applianceSpecDetails = resolveApplianceSpecDetails(modelName);
        if (applianceSpecDetails.hasSpec()) {
            applianceType = preferKnown(applianceSpecDetails.applianceType(), applianceType);
            brand = preferKnown(applianceSpecDetails.brand(), brand);
            modelName = displayModelName(modelName, applianceSpecDetails.modelName());
        }
        String estimatedAge = preferKnown(conditionResult.estimatedAge(), request.estimatedAge());
        String exteriorCondition = preferKnown(conditionResult.exteriorCondition(), request.exteriorCondition());

        state.applyPhotoInspection(
                request.fileName(),
                applianceType,
                exteriorImageUrl,
                request.exteriorPhotoFileName(),
                request.labelPhotoFileName(),
                request.agreedToCreditPolicy()
        );
        state.applyVisionIdentification(
                applianceType,
                brand,
                modelName,
                applianceSpecDetails.sizeGrade(),
                applianceSpecDetails.sizeMetric()
        );
        persistVisionInspection(id, request, applianceType, brand, modelName, estimatedAge, exteriorCondition, exteriorImageUrl);
        return buildResponse(state);
    }

    @Transactional
    public SwapRequestResponse updateAppliance(long id, UpdateApplianceRequest request) {
        SwapRequestState state = findState(id);
        String dbSizeGrade = null;
        String dbSizeMetric = null;
        if (request.modelName() != null && !request.modelName().isBlank()) {
            Optional<ApplianceSpecEntity> spec = applianceSpecsRepository.findByModelNameIgnoreCase(request.modelName());
            if (spec.isPresent()) {
                dbSizeGrade = spec.get().getSizeGrade();
                dbSizeMetric = spec.get().buildSizeMetric();
            }
        }
        state.updateAppliance(
                request.applianceType(),
                request.brand(),
                request.modelName(),
                request.estimatedAge(),
                request.exteriorCondition(),
                dbSizeGrade,
                dbSizeMetric
        );
        persistApplianceConfirmation(id, request);
        return state.toResponse();
    }

    @Transactional
    public SwapRequestResponse acceptPreValuation(long id) {
        SwapRequestState state = findState(id);
        state.acceptPreValuation();
        SwapRequestEntity swapRequest = findSwapRequestEntity(id);
        swapRequest.changeStatus(SwapRequestStatus.PRE_VALUATION_ACCEPTED.name());
        swapRequestRepository.save(swapRequest);
        return state.toResponse();
    }

    public SwapRequestResponse selectReplacementProduct(long id, SelectReplacementProductRequest request) {
        SwapRequestState state = findState(id);
        state.selectReplacementProduct(
                request.productId(),
                request.productName(),
                gradeFromPrice(request.productPrice()),
                request.productPrice(),
                Boolean.TRUE.equals(request.sameDayEligible())
        );
        return buildResponse(state);
    }

    private static String gradeFromPrice(int price) {
        if (price >= 1_500_000) return "프리미엄";
        if (price >= 500_000) return "일반";
        return "보급형";
    }
    @Transactional(readOnly = true)
    public BookingAvailabilityResponse getBookingAvailability(LocalDate date) {
        int slotCapacity = 1;
        List<PickupRequestEntity> reservations = pickupRequestRepository.findByPickupTypeAndBookingDateAndStatusIn(
                "BOOKING",
                date,
                List.of("CONFIRMED", "ASSIGNED", "IN_PROGRESS", "ARRIVED")
        );

        Map<String, Long> reservedCountsByTime = reservations.stream()
                .filter(reservation -> reservation.getBookingTime() != null)
                .collect(Collectors.groupingBy(
                        PickupRequestEntity::getBookingTime,
                        Collectors.counting()
                ));

        List<BookingAvailabilityResponse.Slot> slots = bookingTimeSlots.stream()
                .map(time -> {
                    int reservedCount = reservedCountsByTime.getOrDefault(time, 0L).intValue();
                    return new BookingAvailabilityResponse.Slot(
                            time,
                            reservedCount < slotCapacity,
                            reservedCount,
                            slotCapacity
                    );
                })
                .toList();

        return new BookingAvailabilityResponse(date, slots);
    }

    @Transactional(readOnly = true)
    public Optional<ApplianceSpecLookupResponse> lookupApplianceSpec(String modelName) {
        return applianceSpecsRepository.findByModelNameIgnoreCase(modelName)
                .or(() -> findSpecByNormalizedModelName(modelName))
                .map(spec -> new ApplianceSpecLookupResponse(
                        spec.getBrand(),
                        spec.getApplianceType(),
                        displayModelName(modelName, spec.getModelName()),
                        spec.getSizeGrade(),
                        spec.buildSizeMetric(),
                        spec.getWeightKg()
                ));
    }

    @Transactional
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
        PickupRequestEntity pickupRequest = persistPickupRequest(
                id,
                "BOOKING",
                "CONFIRMED",
                request.bookingDate(),
                request.bookingTime(),
                request.address(),
                request.detailAddress(),
                request.pickupLat(),
                request.pickupLng()
        );
        state.restorePickup(
                pickupRequest.getId(),
                pickupRequest.getPickupType(),
                pickupRequest.getStatus(),
                pickupRequest.getCrewId(),
                pickupRequest.getCrewName(),
                pickupRequest.getBookingDate(),
                pickupRequest.getBookingTime(),
                toLocalDateTime(pickupRequest.getCreatedAt()),
                pickupRequest.getAddress(),
                pickupRequest.getDetailAddress(),
                pickupRequest.getPickupLat(),
                pickupRequest.getPickupLng()
        );
        enrichGpsContext(state);
        return buildResponse(state);
    }

    @Transactional
    public SwapRequestResponse requestInstantCall(long id, InstantCallRequest request) {
        SwapRequestState state = findState(id);
        state.requestInstantCall(request.address(), request.detailAddress(), request.pickupLat(), request.pickupLng());
        PickupRequestEntity pickupRequest = persistPickupRequest(
                id,
                "INSTANT_CALL",
                "REQUESTED",
                null,
                null,
                request.address(),
                request.detailAddress(),
                request.pickupLat(),
                request.pickupLng()
        );
        state.restorePickup(
                pickupRequest.getId(),
                pickupRequest.getPickupType(),
                pickupRequest.getStatus(),
                pickupRequest.getCrewId(),
                pickupRequest.getCrewName(),
                pickupRequest.getBookingDate(),
                pickupRequest.getBookingTime(),
                toLocalDateTime(pickupRequest.getCreatedAt()),
                pickupRequest.getAddress(),
                pickupRequest.getDetailAddress(),
                pickupRequest.getPickupLat(),
                pickupRequest.getPickupLng()
        );
        enrichGpsContext(state);
        return buildResponse(state);
    }

    public SwapRequestResponse completeMockFinalValuation(long id) {
        SwapRequestState state = findState(id);
        state.completeMockFinalValuation();
        return buildResponse(state);
    }

    public SwapRequestResponse requestReReview(long id, ReReviewRequest request) {
        SwapRequestState state = findState(id);
        state.requestReReview(request.reason());
        return buildResponse(state);
    }

    public SwapRequestResponse completeMockReReview(long id) {
        SwapRequestState state = findState(id);
        state.completeReReview();
        return buildResponse(state);
    }

    public SwapRequestResponse issueCredit(long id) {
        SwapRequestState state = findState(id);
        state.issueCredit();
        return buildResponse(state);
    }

    @Transactional
    public SwapRequestResponse submitCrewReview(long id, CrewReviewRequest request) {
        SwapRequestState state = findState(id);
        SwapRequestEntity swapRequest = findSwapRequestEntity(id);

        Long crewId = state.getCrewId();
        if (crewId == null) {
            throw new NoSuchElementException("Crew has not been assigned for swap request: " + id);
        }

        String normalizedComment = request.comment() == null ? null : request.comment().trim();
        CrewReviewEntity existingReview = crewReviewRepository.findBySwapRequest_Id(id).orElse(null);
        if (existingReview != null) {
            throw new ResponseStatusException(CONFLICT, "Crew review has already been submitted.");
        }

        CrewReviewEntity review = CrewReviewEntity.create(swapRequest, crewId, request.rating(), normalizedComment);
        crewReviewRepository.save(review);

        return buildResponse(state);
    }

    public SwapRequestResponse advanceDeliveryTracking(long id) {
        SwapRequestState state = findState(id);
        state.advanceDeliveryTracking();
        return buildResponse(state);
    }

    public SwapRequestResponse get(long id) {
        SwapRequestState state = findState(id);
        return buildResponse(state);
    }

    @Transactional
    public SwapRequestResponse cancel(long id) {
        SwapRequestEntity swapRequest = findSwapRequestEntity(id);
        Optional<PickupRequestEntity> pickupRequest = pickupRequestRepository.findFirstBySwapRequest_IdOrderByCreatedAtDesc(id);
        pickupRequest.ifPresent(request -> {
            if (!"REQUESTED".equals(request.getStatus()) && !"CONFIRMED".equals(request.getStatus())) {
                throw new ResponseStatusException(CONFLICT, "Assigned pickup requests cannot be cancelled.");
            }
            request.updateStatus("CANCELLED");
            pickupRequestRepository.save(request);
        });
        swapRequest.cancel();
        SwapRequestState state = findState(id);
        pickupRequest.ifPresent(request -> restorePickup(state, request));
        return buildResponse(state);
    }

    @Transactional(readOnly = true)
    public Optional<SwapRequestResponse> getLatestByUser(long userId) {
        return swapRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(userId)
                .map(this::restoreAndRespond);
    }

    public SwapRequestResponse getTracking(long id) {
        SwapRequestState state = findState(id);
        return buildResponse(state);
    }

    public List<SwapRequestResponse> getAll() {
        return store.values().stream()
                .sorted(Comparator.comparingLong(SwapRequestState::getId))
                .map(this::buildResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SwapRequestResponse> getAvailableCalls() {
        return pickupRequestRepository.findAll().stream()
                .map(PickupRequestEntity::getSwapRequest)
                .map(this::restoreAndRespond)
                .filter(response -> hasPickupStatus(response, "REQUESTED", "CONFIRMED"))
                .sorted(crewCallComparator())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SwapRequestResponse> getPendingCalls() {
        return pickupRequestRepository.findAll().stream()
                .map(PickupRequestEntity::getSwapRequest)
                .map(this::restoreAndRespond)
                .filter(response -> hasPickupStatus(response, "REQUESTED", "CONFIRMED"))
                .sorted(crewCallComparator())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SwapRequestResponse> getActiveCalls() {
        return pickupRequestRepository.findAll().stream()
                .map(PickupRequestEntity::getSwapRequest)
                .map(this::restoreAndRespond)
                .filter(response -> hasPickupStatus(response, "ASSIGNED", "IN_PROGRESS", "ARRIVED"))
                .sorted(crewCallComparator())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SwapRequestResponse> getCompletedCalls() {
        return pickupRequestRepository.findAll().stream()
                .map(PickupRequestEntity::getSwapRequest)
                .map(this::restoreAndRespond)
                .filter(response -> hasPickupStatus(response, "COMPLETED"))
                .sorted(crewCallComparator())
                .toList();
    }

    @Transactional(readOnly = true)
    public SwapRequestResponse getCrewCallDetail(long pickupRequestId) {
        SwapRequestState state = findByPickupRequestId(pickupRequestId);
        return buildResponse(state);
    }

    public List<SwapRequestResponse.LocationHistoryPoint> getLocationHistory(long pickupRequestId) {
        return List.copyOf(locationHistoryStore.getOrDefault(pickupRequestId, List.of()));
    }

    @Transactional
    public SwapRequestResponse acceptCall(long pickupRequestId) {
        SwapRequestState state = findByPickupRequestId(pickupRequestId);
        if (hasBlockingActiveCall(pickupRequestId, DEMO_CREW_ID)) {
            throw new ResponseStatusException(CONFLICT, "Crew already has an active pickup.");
        }

        CrewGpsState assignedCrew = crewGpsStore.get(DEMO_CREW_ID);
        String crewName = assignedCrew == null ? DEMO_CREW_NAME : assignedCrew.crewName;
        state.acceptByCrew(DEMO_CREW_ID, crewName, DEMO_CREW_PHOTO, DEMO_CREW_RATING, DEMO_CREW_REVIEW_SUMMARY);
        PickupRequestEntity pickupRequest = findPickupRequestEntity(pickupRequestId);
        pickupRequest.assignCrew(DEMO_CREW_ID, crewName, null, null);
        pickupRequestRepository.save(pickupRequest);
        restorePickup(state, pickupRequest);

        if (assignedCrew != null) {
            assignedCrew.status = "ASSIGNED";
            state.updateCrewLocation(assignedCrew.lat, assignedCrew.lng, assignedCrew.heading, assignedCrew.speed);
            appendLocationHistory(pickupRequestId, assignedCrew.lat, assignedCrew.lng, assignedCrew.heading, assignedCrew.speed);
        }

        return buildResponse(state);
    }

    private boolean hasBlockingActiveCall(long pickupRequestId, long crewId) {
        return pickupRequestRepository.findAll().stream()
                .filter(pickupRequest -> pickupRequest.getId() != pickupRequestId)
                .map(PickupRequestEntity::getSwapRequest)
                .map(this::restoreAndRespond)
                .anyMatch(response -> {
                    if (!hasPickupStatus(response, "ASSIGNED", "IN_PROGRESS", "ARRIVED")) {
                        return false;
                    }

                    Long assignedCrewId = response.pickupRequest() == null ? null : response.pickupRequest().crewId();
                    return assignedCrewId == null || assignedCrewId == crewId;
                });
    }

    @Transactional
    public SwapRequestResponse depart(long pickupRequestId) {
        SwapRequestState state = findByPickupRequestId(pickupRequestId);
        state.departCrew();
        PickupRequestEntity pickupRequest = findPickupRequestEntity(pickupRequestId);
        pickupRequest.changeStatus("IN_PROGRESS");
        pickupRequestRepository.save(pickupRequest);
        restorePickup(state, pickupRequest);
        return buildResponse(state);
    }

    @Transactional
    public SwapRequestResponse updateLocation(long pickupRequestId, CrewLocationRequest request) {
        SwapRequestState state = findByPickupRequestId(pickupRequestId);
        CrewLocationProcessor.ProcessedLocation processedLocation = crewLocationProcessor.process(
                pickupRequestId,
                state.getCrewId() == null ? DEMO_CREW_ID : state.getCrewId(),
                request,
                cachedRoutePoints(pickupRequestId)
        );
        if (!processedLocation.accepted()) {
            return buildResponse(state);
        }

        Long crewId = state.getCrewId() == null ? DEMO_CREW_ID : state.getCrewId();
        CrewGpsState crewState = crewGpsStore.computeIfAbsent(
                crewId,
                id -> new CrewGpsState(id, DEMO_CREW_NAME, processedLocation.lat(), processedLocation.lng(), "ASSIGNED")
        );
        crewState.lat = processedLocation.lat();
        crewState.lng = processedLocation.lng();
        crewState.heading = processedLocation.heading();
        crewState.speed = processedLocation.speed();
        crewState.status = "ASSIGNED";
        state.updateCrewLocation(
                processedLocation.lat(),
                processedLocation.lng(),
                processedLocation.heading(),
                processedLocation.speed(),
                processedLocation.accuracy()
        );

        appendLocationHistory(
                pickupRequestId,
                crewState.lat,
                crewState.lng,
                crewState.heading,
                crewState.speed
        );
        return buildResponse(state);
    }

    @Transactional
    public SwapRequestResponse arrive(long pickupRequestId) {
        SwapRequestState state = findByPickupRequestId(pickupRequestId);
        state.arriveCrew();
        PickupRequestEntity pickupRequest = findPickupRequestEntity(pickupRequestId);
        pickupRequest.changeStatus("ARRIVED");
        pickupRequestRepository.save(pickupRequest);
        restorePickup(state, pickupRequest);
        return buildResponse(state);
    }

    @Transactional
    public SwapRequestResponse completePickup(long pickupRequestId, CrewCompletePickupRequest request) {
        SwapRequestState state = findByPickupRequestId(pickupRequestId);
        state.completePickup(
                request.pickupPhotoFileName(),
                request.hubPhotoFileName(),
                request.inspectionMemo(),
                request.hubMemo()
        );
        PickupRequestEntity pickupRequest = findPickupRequestEntity(pickupRequestId);
        pickupRequest.changeStatus("COMPLETED");
        pickupRequestRepository.save(pickupRequest);
        restorePickup(state, pickupRequest);
        enrichGpsContext(state);
        state.applySettlement(crewSettlementCalculator.finalizeSettlement(state.toResponse()));

        Long crewId = state.getCrewId() == null ? DEMO_CREW_ID : state.getCrewId();
        CrewGpsState crewState = crewGpsStore.get(crewId);
        if (crewState != null) {
            crewState.status = "AVAILABLE";
        }

        return buildResponse(state);
    }

    public SwapRequestResponse adminCompleteFinalValuation(long id, FinalValuationRequest request) {
        SwapRequestState state = findState(id);
        state.completeFinalValuation(
                request.amount(),
                List.of(
                        valueOrDefault(request.exteriorReason(), "전면 사용 흔적은 있으나 주요 파손은 확인되지 않았습니다."),
                        valueOrDefault(request.partsReason(), "일부 내부 부품은 재사용 가능성이 있어 보상가에 반영했습니다."),
                        valueOrDefault(request.materialReason(), "금속/플라스틱 회수 가능 가치를 기준으로 기본 금액을 산정했습니다."),
                        valueOrDefault(request.processingReason(), "수거, 분류, 안전 해체 비용을 차감해 최종 금액을 확정했습니다.")
                )
        );
        return buildResponse(state);
    }

    public List<SwapRequestResponse.Notification> getNotifications(long userId) {
        return store.values().stream()
                .filter(state -> state.toResponse().customerId() == userId || userId == DEMO_CREW_ID)
                .flatMap(state -> state.toResponse().notifications().stream())
                .toList();
    }

    public synchronized Map<String, Object> resetDemoState() {
        store.clear();
        sequence.set(1);
        resetCrewGpsStore();
        locationHistoryStore.clear();
        routeCacheStore.clear();
        return Map.of(
                "message", "Demo pickup state has been reset.",
                "totalSwapRequests", store.size(),
                "availableCrewCalls", getAvailableCalls().size()
        );
    }

    private SwapRequestResponse buildResponse(SwapRequestState state) {
        enrichGpsContext(state);
        return augmentSettlement(augmentTracking(state, augmentCrewReview(state.toResponse())));
    }

    private SwapRequestResponse augmentSettlement(SwapRequestResponse response) {
        if (response.pickupRequest() == null) {
            return response;
        }

        SwapRequestResponse.Settlement settlement = "COMPLETED".equals(response.pickupRequest().status())
                ? crewSettlementCalculator.finalizeSettlement(response)
                : crewSettlementCalculator.estimate(response);

        return new SwapRequestResponse(
                response.id(),
                response.customerId(),
                response.status(),
                response.appliance(),
                response.userConsent(),
                response.captureEvidence(),
                response.preValuation(),
                response.rewardEstimate(),
                response.selectedProduct(),
                response.booking(),
                response.pickupRequest(),
                response.crewProfile(),
                response.crewReview(),
                response.dispatchInfo(),
                response.tracking(),
                response.finalValuation(),
                response.credit(),
                response.rewardOverview(),
                response.deliveryTracking(),
                response.pickupResultReport(),
                response.recyclingReport(),
                settlement,
                response.notifications()
        );
    }

    private SwapRequestResponse augmentCrewReview(SwapRequestResponse response) {
        Long crewId = response.pickupRequest() == null ? null : response.pickupRequest().crewId();
        SwapRequestResponse.CrewProfile crewProfile = response.crewProfile();

        if (crewId != null && crewProfile != null) {
            CrewReviewSnapshot snapshot = loadCrewReviewSnapshot(crewId);
            crewProfile = new SwapRequestResponse.CrewProfile(
                    crewProfile.name(),
                    crewProfile.photoUrl(),
                    snapshot.averageRating(),
                    snapshot.reviewSummary().isEmpty() ? crewProfile.reviewSummary() : snapshot.reviewSummary()
            );
        }

        SwapRequestResponse.CrewReview crewReview = crewReviewRepository.findBySwapRequest_Id(response.id())
                .map(review -> new SwapRequestResponse.CrewReview(
                        review.getRating(),
                        review.getComment(),
                        review.getCreatedAt().toLocalDateTime()
                ))
                .orElse(null);

        return new SwapRequestResponse(
                response.id(),
                response.customerId(),
                response.status(),
                response.appliance(),
                response.userConsent(),
                response.captureEvidence(),
                response.preValuation(),
                response.rewardEstimate(),
                response.selectedProduct(),
                response.booking(),
                response.pickupRequest(),
                crewProfile,
                crewReview,
                response.dispatchInfo(),
                response.tracking(),
                response.finalValuation(),
                response.credit(),
                response.rewardOverview(),
                response.deliveryTracking(),
                response.pickupResultReport(),
                response.recyclingReport(),
                response.settlement(),
                response.notifications()
        );
    }

    private SwapRequestResponse augmentTracking(SwapRequestState state, SwapRequestResponse response) {
        if (response.tracking() == null) {
            return response;
        }

        long pickupRequestId = response.pickupRequest() == null ? -1L : response.pickupRequest().pickupRequestId();
        List<SwapRequestResponse.LocationHistoryPoint> history = pickupRequestId < 0
                ? List.of()
                : List.copyOf(locationHistoryStore.getOrDefault(pickupRequestId, List.of()));

        SwapRequestResponse.RouteSummary route = resolveRoute(pickupRequestId, response, history);
        SwapRequestResponse.Tracking tracking = new SwapRequestResponse.Tracking(
                response.tracking().message(),
                response.tracking().estimatedArrivalAt(),
                response.tracking().driverLocation(),
                response.tracking().processingCenter(),
                response.tracking().phase(),
                response.tracking().metrics(),
                response.tracking().nearbyCrews(),
                response.tracking().events(),
                route,
                history
        );

        return new SwapRequestResponse(
                response.id(),
                response.customerId(),
                response.status(),
                response.appliance(),
                response.userConsent(),
                response.captureEvidence(),
                response.preValuation(),
                response.rewardEstimate(),
                response.selectedProduct(),
                response.booking(),
                response.pickupRequest(),
                response.crewProfile(),
                response.crewReview(),
                response.dispatchInfo(),
                tracking,
                response.finalValuation(),
                response.credit(),
                response.rewardOverview(),
                response.deliveryTracking(),
                response.pickupResultReport(),
                response.recyclingReport(),
                response.settlement(),
                response.notifications()
        );
    }

    private CrewReviewSnapshot loadCrewReviewSnapshot(Long crewId) {
        Double averageRating = crewReviewRepository.findAverageRatingByCrewId(crewId);
        List<String> latestComments = crewReviewRepository.findAllByCrewIdOrderByCreatedAtDesc(crewId).stream()
                .map(CrewReviewEntity::getComment)
                .filter(comment -> comment != null && !comment.isBlank())
                .limit(2)
                .toList();

        double computedRating = averageRating == null ? DEMO_CREW_RATING : averageRating;
        double roundedRating = Math.round(computedRating * 10.0) / 10.0;
        return new CrewReviewSnapshot(roundedRating, latestComments);
    }

    private SwapRequestResponse.RouteSummary resolveRoute(
            long pickupRequestId,
            SwapRequestResponse response,
            List<SwapRequestResponse.LocationHistoryPoint> history
    ) {
        if (response.tracking() == null) {
            return null;
        }

        SwapRequestResponse.RoutePoint destination = resolveDestination(response);
        if (destination == null) {
            return null;
        }

        CachedRoute cachedRoute = pickupRequestId < 0 ? null : routeCacheStore.get(pickupRequestId);
        if (cachedRoute != null && sameRouteDestination(cachedRoute.destination(), destination)) {
            return cachedRoute.route();
        }

        SwapRequestResponse.DriverLocation driverLocation = response.tracking().driverLocation();
        if (driverLocation == null) {
            return null;
        }

        SwapRequestResponse.RoutePoint origin = new SwapRequestResponse.RoutePoint(driverLocation.lat(), driverLocation.lng());
        SwapRequestResponse.RouteSummary computedRoute = kakaoDirectionsService.computeDrivingRoute(origin, destination);
        if (computedRoute == null) {
            return null;
        }

        SwapRequestResponse.RouteSummary route = new SwapRequestResponse.RouteSummary(
                computedRoute.mode(),
                computedRoute.distanceMeters(),
                computedRoute.durationSeconds(),
                computedRoute.distanceLabel(),
                computedRoute.durationLabel(),
                computedRoute.encodedPolyline(),
                computedRoute.points() == null ? List.of() : computedRoute.points(),
                computedRoute.calculatedAt()
        );

        if (pickupRequestId >= 0) {
            routeCacheStore.put(pickupRequestId, new CachedRoute(destination, route));
        }

        return route;
    }

    private boolean sameRouteDestination(SwapRequestResponse.RoutePoint left, SwapRequestResponse.RoutePoint right) {
        return Math.abs(left.lat() - right.lat()) < 0.000001
                && Math.abs(left.lng() - right.lng()) < 0.000001;
    }

    private List<SwapRequestResponse.RoutePoint> cachedRoutePoints(long pickupRequestId) {
        CachedRoute cachedRoute = routeCacheStore.get(pickupRequestId);
        if (cachedRoute == null || cachedRoute.route() == null || cachedRoute.route().points() == null) {
            return List.of();
        }
        return cachedRoute.route().points();
    }

    private SwapRequestResponse.RoutePoint resolveDestination(SwapRequestResponse response) {
        String pickupStatus = response.pickupRequest() == null ? null : response.pickupRequest().status();
        boolean headingToHub = "ARRIVED".equals(pickupStatus)
                || "COMPLETED".equals(pickupStatus)
                || "EN_ROUTE_TO_PROCESSING_CENTER".equals(response.tracking().phase())
                || "DELIVERED_TO_EWASTE_HUB".equals(response.tracking().phase());

        if (headingToHub && response.tracking().processingCenter() != null) {
            return new SwapRequestResponse.RoutePoint(
                    response.tracking().processingCenter().lat(),
                    response.tracking().processingCenter().lng()
            );
        }

        if (response.booking() != null && response.booking().pickupLat() != null && response.booking().pickupLng() != null) {
            return new SwapRequestResponse.RoutePoint(response.booking().pickupLat(), response.booking().pickupLng());
        }

        if (response.tracking().processingCenter() != null) {
            return new SwapRequestResponse.RoutePoint(
                    response.tracking().processingCenter().lat(),
                    response.tracking().processingCenter().lng()
            );
        }

        return null;
    }

    private void appendLocationHistory(long pickupRequestId, double lat, double lng, double heading, double speed) {
        List<SwapRequestResponse.LocationHistoryPoint> history = locationHistoryStore.computeIfAbsent(
                pickupRequestId,
                ignored -> new CopyOnWriteArrayList<>()
        );

        if (!history.isEmpty()) {
            SwapRequestResponse.LocationHistoryPoint last = history.get(history.size() - 1);
            if (Math.abs(last.lat() - lat) < 0.00001 && Math.abs(last.lng() - lng) < 0.00001) {
                return;
            }
        }

        history.add(new SwapRequestResponse.LocationHistoryPoint(
                lat,
                lng,
                heading,
                speed,
                LocalDateTime.now()
        ));
    }

    private void enrichGpsContext(SwapRequestState state) {
        if (state.getPickupLat() == null || state.getPickupLng() == null) {
            return;
        }

        SwapRequestResponse.LocationPoint processingCenter = processingCenters.stream()
                .min(Comparator.comparingDouble(center -> distanceMeters(
                        state.getPickupLat(),
                        state.getPickupLng(),
                        center.lat(),
                        center.lng()
                )))
                .orElse(processingCenters.get(0));

        state.setProcessingCenter(processingCenter.label(), processingCenter.lat(), processingCenter.lng());

        List<SwapRequestResponse.NearbyCrew> nearbyCrews = crewGpsStore.values().stream()
                .sorted(Comparator.comparingDouble(crew -> distanceMeters(
                        state.getPickupLat(),
                        state.getPickupLng(),
                        crew.lat,
                        crew.lng
                )))
                .map(crew -> new SwapRequestResponse.NearbyCrew(
                        crew.crewId,
                        crew.crewName,
                        crew.status,
                        crew.lat,
                        crew.lng,
                        distanceMeters(state.getPickupLat(), state.getPickupLng(), crew.lat, crew.lng),
                        state.getCrewId() != null && state.getCrewId().equals(crew.crewId)
                ))
                .toList();

        state.setNearbyCrews(nearbyCrews);

        SwapRequestResponse.NearbyCrew topCrew = nearbyCrews.isEmpty() ? null : nearbyCrews.get(0);
        int priorityRank = 0;
        if (state.getCrewId() != null) {
            priorityRank = 1;
            for (int index = 0; index < nearbyCrews.size(); index++) {
                if (state.getCrewId().equals(nearbyCrews.get(index).crewId())) {
                    priorityRank = index + 1;
                    break;
                }
            }
        } else if (topCrew != null) {
            priorityRank = 1;
        }

        double baseDistance = topCrew == null ? 1800.0 : topCrew.distanceMeters();
        int matchScore = (int) Math.max(52, Math.min(97, Math.round(96 - (baseDistance / 120.0))));
        String dispatchAlertMessage = switch (valueOrDefault(state.getPickupStatus(), "")) {
            case "REQUESTED", "CONFIRMED" -> "매칭 점수가 높은 크루에게 우선 배차 알림을 보내고 있어요.";
            case "ASSIGNED" -> "배정된 크루가 수거 일정에 맞춰 위치를 공유하고 있어요.";
            case "IN_PROGRESS" -> "크루가 수거지로 이동 중이며 실시간 위치가 갱신되고 있어요.";
            case "ARRIVED" -> "소비자 수거가 완료되어 처리 허브로 이동 중이에요.";
            case "COMPLETED" -> "처리 허브 전달이 완료됐어요.";
            default -> "예약 또는 바로콜 접수 후 배차 정보가 표시됩니다.";
        };
        String dispatchReason = topCrew == null
                ? "근처 크루 정보가 아직 없습니다."
                : "가까운 크루 거리 " + Math.round(topCrew.distanceMeters()) + "m, 현재 이동 동선, 최근 수락 이력을 반영했어요.";

        state.setDispatchContext(
                matchScore,
                priorityRank,
                0,
                0,
                0,
                dispatchAlertMessage,
                dispatchReason
        );
    }

    private void persistVisionInspection(
            long id,
            PhotoUploadRequest request,
            String applianceType,
            String brand,
            String modelName,
            String estimatedAge,
            String exteriorCondition,
            String exteriorImageUrl
    ) {
        SwapRequestEntity swapRequest = findSwapRequestEntity(id);
        String finalApplianceType = valueOrDefault(applianceType, swapRequest.getApplianceType());

        ApplianceEntity appliance = applianceRepository.findBySwapRequest_Id(id)
                .orElseGet(() -> applianceRepository.save(ApplianceEntity.create(swapRequest, finalApplianceType)));
        appliance.applyPhotoInspection(
                finalApplianceType,
                valueOrDefault(brand, "unknown"),
                valueOrDefault(modelName, "unknown"),
                valueOrDefault(estimatedAge, "확인 필요"),
                valueOrDefault(exteriorCondition, "외관 상태 확인 필요")
        );
        applianceRepository.save(appliance);

        applianceImageRepository.save(ApplianceImageEntity.customerCapture(
                swapRequest,
                appliance,
                request.fileName(),
                valueOrDefault(exteriorImageUrl, request.imageUrl())
        ));

        valuationRepository.save(ValuationEntity.preValuation(
                swapRequest,
                1500,
                2400,
                "OpenAI GPT-4o Vision photo analysis result was used for the preliminary valuation."
        ));
        swapRequest.changeStatus(SwapRequestStatus.PRE_VALUATION_READY.name());
        swapRequestRepository.save(swapRequest);
    }

    private OpenAiVisionService.OpenAiVisionResult identifyAppliance(String imageUrl, String applianceType) {
        return openAiVisionService.identifyAppliance(imageUrl, applianceType)
                .orElseGet(OpenAiVisionService.OpenAiVisionResult::unknown);
    }

    private OpenAiVisionService.OpenAiConditionResult analyzeCondition(String imageUrl, String applianceType) {
        return openAiVisionService.analyzeCondition(imageUrl, applianceType)
                .orElseGet(OpenAiVisionService.OpenAiConditionResult::empty);
    }

    private static String firstKnown(String primary, String fallback) {
        return isKnown(primary) ? primary : fallback;
    }

    private static String preferKnown(String candidate, String fallback) {
        return isKnown(candidate) ? candidate : fallback;
    }

    private static boolean isKnown(String value) {
        return value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value.trim()) && !"null".equalsIgnoreCase(value.trim());
    }

    private ApplianceSizeDetails resolveApplianceSizeDetails(String modelName) {
        ApplianceSpecDetails details = resolveApplianceSpecDetails(modelName);
        return new ApplianceSizeDetails(details.sizeGrade(), details.sizeMetric());
    }

    private ApplianceSpecDetails resolveApplianceSpecDetails(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return ApplianceSpecDetails.empty();
        }

        return applianceSpecsRepository.findByModelNameIgnoreCase(modelName)
                .or(() -> findSpecByNormalizedModelName(modelName))
                .map(spec -> new ApplianceSpecDetails(
                        spec.getBrand(),
                        spec.getApplianceType(),
                        spec.getModelName(),
                        spec.getSizeGrade(),
                        spec.buildSizeMetric()
                ))
                .orElseGet(ApplianceSpecDetails::empty);
    }

    private Optional<ApplianceSpecEntity> findSpecByNormalizedModelName(String modelName) {
        String normalized = normalizeModelKey(modelName);
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        return applianceSpecsRepository.findAll().stream()
                .filter(spec -> normalizeModelKey(spec.getModelName()).equals(normalized))
                .findFirst();
    }

    private static String normalizeModelKey(String modelName) {
        if (modelName == null) {
            return "";
        }
        return modelName.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private static String displayModelName(String candidate, String dbModelName) {
        String value = isKnown(candidate) ? candidate : dbModelName;
        if (!isKnown(value)) {
            return value;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void persistApplianceConfirmation(long id, UpdateApplianceRequest request) {
        SwapRequestEntity swapRequest = findSwapRequestEntity(id);
        ApplianceEntity appliance = applianceRepository.findBySwapRequest_Id(id)
                .orElseGet(() -> applianceRepository.save(ApplianceEntity.create(swapRequest, swapRequest.getApplianceType())));
        appliance.confirmByCustomer(
                valueOrDefault(request.applianceType(), swapRequest.getApplianceType()),
                valueOrDefault(request.brand(), "LG"),
                valueOrDefault(request.modelName(), "Unknown"),
                valueOrDefault(request.estimatedAge(), "?뺤씤 ?꾩슂"),
                valueOrDefault(request.exteriorCondition(), "?뺤씤 ?꾩슂")
        );
        applianceRepository.save(appliance);
        swapRequest.changeStatus(SwapRequestStatus.PRE_VALUATION_READY.name());
        swapRequestRepository.save(swapRequest);
    }

    private PickupRequestEntity persistPickupRequest(
            long id,
            String pickupType,
            String status,
            java.time.LocalDate bookingDate,
            String bookingTime,
            String address,
            String detailAddress,
            Double pickupLat,
            Double pickupLng
    ) {
        SwapRequestEntity swapRequest = findSwapRequestEntity(id);
        PickupRequestEntity pickupRequest = pickupRequestRepository.findFirstBySwapRequest_IdOrderByCreatedAtDesc(id)
                .orElseGet(() -> PickupRequestEntity.create(swapRequest));
        pickupRequest.applyBooking(pickupType, status, bookingDate, bookingTime, address, detailAddress, pickupLat, pickupLng);
        PickupRequestEntity savedPickupRequest = pickupRequestRepository.save(pickupRequest);
        swapRequest.changeStatus("BOOKING".equals(pickupType)
                ? SwapRequestStatus.BOOKING_CONFIRMED.name()
                : SwapRequestStatus.INSTANT_CALL_REQUESTED.name());
        swapRequestRepository.save(swapRequest);
        return savedPickupRequest;
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
        String phoneNumber = UserService.formatPhoneNumber(request.phoneNumber());
        if (request.userId() != null) {
            UserEntity user = userRepository.findById(request.userId())
                    .orElseThrow(() -> new NoSuchElementException("User not found: " + request.userId()));
            user.updateProfile(request.userName(), phoneNumber);
            return user;
        }

        String thinqUserKey = UserService.toThinqUserKey(phoneNumber, request.userName());
        return userRepository.findByThinqUserKey(thinqUserKey)
                .map(existingUser -> {
                    existingUser.updateProfile(request.userName(), phoneNumber);
                    return existingUser;
                })
                .orElseGet(() -> UserEntity.create(thinqUserKey, request.userName(), phoneNumber));
    }
    private SwapRequestState findState(long id) {
        SwapRequestState state = store.get(id);
        if (state != null) {
            return state;
        }

        SwapRequestEntity swapRequest = findSwapRequestEntity(id);
        return restoreState(swapRequest);
    }

    private SwapRequestResponse restoreAndRespond(SwapRequestEntity swapRequest) {
        SwapRequestState state = restoreState(swapRequest);
        return buildResponse(state);
    }

    private SwapRequestState restoreState(SwapRequestEntity swapRequest) {
        SwapRequestState existing = store.get(swapRequest.getId());
        if (existing != null) {
            return existing;
        }

        SwapRequestState state = new SwapRequestState(
                swapRequest.getId(),
                swapRequest.getUser().getId(),
                swapRequest.getApplianceType()
        );

        Optional<ApplianceImageEntity> image = applianceImageRepository.findFirstBySwapRequest_IdOrderByUploadedAtDesc(swapRequest.getId());
        image.ifPresent(applianceImage -> state.applyPhotoInspection(
                applianceImage.getFileName(),
                swapRequest.getApplianceType(),
                applianceImage.getImageUrl(),
                applianceImage.getFileName(),
                null,
                true
        ));

        applianceRepository.findBySwapRequest_Id(swapRequest.getId()).ifPresent(appliance -> {
            String dbSizeGrade = null;
            String dbSizeMetric = null;
            if (appliance.getModelName() != null && !appliance.getModelName().isBlank()) {
                Optional<ApplianceSpecEntity> spec = applianceSpecsRepository.findByModelNameIgnoreCase(appliance.getModelName());
                if (spec.isPresent()) {
                    dbSizeGrade = spec.get().getSizeGrade();
                    dbSizeMetric = spec.get().buildSizeMetric();
                }
            }

            state.updateAppliance(
                    appliance.getApplianceType(),
                    appliance.getBrand(),
                    appliance.getModelName(),
                    appliance.getEstimatedAge(),
                    appliance.getExteriorCondition(),
                    dbSizeGrade,
                    dbSizeMetric
            );
        });

        if (SwapRequestStatus.PRE_VALUATION_ACCEPTED.name().equals(swapRequest.getStatus())) {
            state.acceptPreValuation();
        }

        pickupRequestRepository.findFirstBySwapRequest_IdOrderByCreatedAtDesc(swapRequest.getId())
                .ifPresent(pickupRequest -> restorePickup(state, pickupRequest));

        store.put(state.getId(), state);
        return state;
    }

    private SwapRequestEntity findSwapRequestEntity(long id) {
        return swapRequestRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Swap request not found in DB: " + id));
    }

    private PickupRequestEntity findPickupRequestEntity(long pickupRequestId) {
        return pickupRequestRepository.findById(pickupRequestId)
                .orElseThrow(() -> new NoSuchElementException("Pickup request not found: " + pickupRequestId));
    }

    private void restorePickup(SwapRequestState state, PickupRequestEntity pickupRequest) {
        state.restorePickup(
                pickupRequest.getId(),
                pickupRequest.getPickupType(),
                pickupRequest.getStatus(),
                pickupRequest.getCrewId(),
                pickupRequest.getCrewName(),
                pickupRequest.getBookingDate(),
                pickupRequest.getBookingTime(),
                toLocalDateTime(pickupRequest.getCreatedAt()),
                pickupRequest.getAddress(),
                pickupRequest.getDetailAddress(),
                pickupRequest.getPickupLat(),
                pickupRequest.getPickupLng()
        );
    }

    private java.time.LocalDateTime toLocalDateTime(java.time.OffsetDateTime value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private SwapRequestState findByPickupRequestId(long pickupRequestId) {
        Optional<SwapRequestState> state = store.values().stream()
                .filter(candidate -> candidate.getPickupRequestId() != null && candidate.getPickupRequestId() == pickupRequestId)
                .findFirst();

        if (state.isPresent()) {
            return state.get();
        }

        PickupRequestEntity pickupRequest = pickupRequestRepository.findById(pickupRequestId)
                .orElseThrow(() -> new NoSuchElementException("Pickup request not found: " + pickupRequestId));
        return restoreState(pickupRequest.getSwapRequest());
    }

    private boolean hasPickupStatus(SwapRequestResponse response, String... statuses) {
        if (response.pickupRequest() == null || response.pickupRequest().status() == null) {
            return false;
        }

        return Arrays.stream(statuses)
                .anyMatch(status -> status.equals(response.pickupRequest().status()));
    }

    private Comparator<SwapRequestResponse> crewCallComparator() {
        return Comparator
                .comparingLong((SwapRequestResponse response) ->
                        response.pickupRequest() == null ? Long.MIN_VALUE : response.pickupRequest().pickupRequestId())
                .reversed();
    }

    private double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double earthRadius = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(earthRadius * c * 10.0) / 10.0;
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void resetCrewGpsStore() {
        crewGpsStore.clear();
    }

    private record CachedRoute(
            SwapRequestResponse.RoutePoint destination,
            SwapRequestResponse.RouteSummary route
    ) {
    }

    private record ApplianceSizeDetails(
            String sizeGrade,
            String sizeMetric
    ) {
    }

    private record ApplianceSpecDetails(
            String brand,
            String applianceType,
            String modelName,
            String sizeGrade,
            String sizeMetric
    ) {
        private static ApplianceSpecDetails empty() {
            return new ApplianceSpecDetails(null, null, null, null, null);
        }

        private boolean hasSpec() {
            return isKnown(modelName);
        }
    }

    private static final class CrewGpsState {
        private final Long crewId;
        private final String crewName;
        private double lat;
        private double lng;
        private double heading;
        private double speed;
        private String status;

        private CrewGpsState(Long crewId, String crewName, double lat, double lng, String status) {
            this.crewId = crewId;
            this.crewName = crewName;
            this.lat = lat;
            this.lng = lng;
            this.status = status;
            this.heading = 0.0;
            this.speed = 0.0;
        }
    }

    private record CrewReviewSnapshot(
            double averageRating,
            List<String> reviewSummary
    ) {
    }
}
