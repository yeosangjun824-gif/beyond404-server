package com.swapit.service;

import com.swapit.domain.SwapRequestState;
import com.swapit.domain.entity.ApplianceEntity;
import com.swapit.domain.entity.ApplianceImageEntity;
import com.swapit.domain.entity.PickupRequestEntity;
import com.swapit.domain.entity.SwapRequestEntity;
import com.swapit.domain.entity.UserEntity;
import com.swapit.domain.entity.ValuationEntity;
import com.swapit.domain.enums.SwapRequestStatus;
import com.swapit.dto.BookingRequest;
import com.swapit.dto.BookingAvailabilityResponse;
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
import com.swapit.repository.ApplianceImageRepository;
import com.swapit.repository.ApplianceRepository;
import com.swapit.repository.PickupRequestRepository;
import com.swapit.repository.SwapRequestRepository;
import com.swapit.repository.UserRepository;
import com.swapit.repository.ValuationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SwapRequestService {
    private static final long DEMO_CUSTOMER_ID = 1L;
    private static final long DEMO_CREW_ID = 101L;
    private static final String DEMO_CREW_NAME = "민준 크루";
    private static final String DEMO_CREW_PHOTO = "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=400&q=80";
    private static final double DEMO_CREW_RATING = 4.9;
    private static final List<String> DEMO_CREW_REVIEW_SUMMARY = List.of(
            "친절하게 수거 진행",
            "시간 약속을 잘 지켜요"
    );

    private final UserRepository userRepository;
    private final SwapRequestRepository swapRequestRepository;
    private final ApplianceRepository applianceRepository;
    private final ApplianceImageRepository applianceImageRepository;
    private final ValuationRepository valuationRepository;
    private final GoogleRoutesService googleRoutesService;
    private final PickupRequestRepository pickupRequestRepository;

    private final AtomicLong sequence = new AtomicLong(1);
    private final Map<Long, SwapRequestState> store = new ConcurrentHashMap<>();
    private final Map<Long, CrewGpsState> crewGpsStore = new ConcurrentHashMap<>();
    private final Map<Long, List<SwapRequestResponse.LocationHistoryPoint>> locationHistoryStore = new ConcurrentHashMap<>();
    private final List<SwapRequestResponse.LocationPoint> processingCenters = List.of(
            new SwapRequestResponse.LocationPoint("서울 서부 e-waste 허브", 37.5481, 126.8914),
            new SwapRequestResponse.LocationPoint("서울 동부 e-waste 허브", 37.5457, 127.1427)
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
    public SwapRequestResponse analyzePhoto(long id, PhotoUploadRequest request) {
        SwapRequestState state = findState(id);
        state.applyMockInspection(
                request.fileName(),
                request.applianceType(),
                request.imageUrl(),
                request.exteriorPhotoFileName(),
                request.labelPhotoFileName(),
                request.agreedToCreditPolicy()
        );
        persistMockInspection(id, request);
        return buildResponse(state);
    }

    @Transactional
    public SwapRequestResponse updateAppliance(long id, UpdateApplianceRequest request) {
        SwapRequestState state = findState(id);
        state.updateAppliance(
                request.applianceType(),
                request.brand(),
                request.modelName(),
                request.estimatedAge(),
                request.exteriorCondition()
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
                request.productGrade(),
                request.productPrice(),
                Boolean.TRUE.equals(request.sameDayEligible())
        );
        return buildResponse(state);
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
        return state.toResponse();
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
        return state.toResponse();
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

    public SwapRequestResponse advanceDeliveryTracking(long id) {
        SwapRequestState state = findState(id);
        state.advanceDeliveryTracking();
        return buildResponse(state);
    }

    public SwapRequestResponse get(long id) {
        SwapRequestState state = findState(id);
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
        return pickupRequestRepository.findByStatusInOrderByCreatedAtDesc(List.of("REQUESTED", "CONFIRMED")).stream()
                .map(PickupRequestEntity::getSwapRequest)
                .map(this::restoreAndRespond)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SwapRequestResponse> getPendingCalls() {
        return pickupRequestRepository.findByStatusInOrderByCreatedAtDesc(List.of("REQUESTED", "CONFIRMED")).stream()
                .map(PickupRequestEntity::getSwapRequest)
                .map(this::restoreAndRespond)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SwapRequestResponse> getActiveCalls() {
        return pickupRequestRepository.findByStatusInOrderByCreatedAtDesc(List.of("ASSIGNED", "IN_PROGRESS", "ARRIVED")).stream()
                .map(PickupRequestEntity::getSwapRequest)
                .map(this::restoreAndRespond)
                .toList();
    }

    public SwapRequestResponse getCrewCallDetail(long pickupRequestId) {
        SwapRequestState state = findByPickupRequestId(pickupRequestId);
        return buildResponse(state);
    }

    public List<SwapRequestResponse.LocationHistoryPoint> getLocationHistory(long pickupRequestId) {
        return List.copyOf(locationHistoryStore.getOrDefault(pickupRequestId, List.of()));
    }

    public SwapRequestResponse acceptCall(long pickupRequestId) {
        SwapRequestState state = findByPickupRequestId(pickupRequestId);
        CrewGpsState assignedCrew = crewGpsStore.getOrDefault(DEMO_CREW_ID, new CrewGpsState(DEMO_CREW_ID, DEMO_CREW_NAME, 37.5665, 126.9780, "AVAILABLE"));
        assignedCrew.status = "ASSIGNED";
        state.acceptByCrew(DEMO_CREW_ID, assignedCrew.crewName, DEMO_CREW_PHOTO, DEMO_CREW_RATING, DEMO_CREW_REVIEW_SUMMARY);
        state.updateCrewLocation(assignedCrew.lat, assignedCrew.lng, assignedCrew.heading, assignedCrew.speed);
        appendLocationHistory(pickupRequestId, assignedCrew.lat, assignedCrew.lng, assignedCrew.heading, assignedCrew.speed);
        return buildResponse(state);
    }

    public SwapRequestResponse depart(long pickupRequestId) {
        SwapRequestState state = findByPickupRequestId(pickupRequestId);
        state.departCrew();
        return buildResponse(state);
    }

    public SwapRequestResponse updateLocation(long pickupRequestId, CrewLocationRequest request) {
        SwapRequestState state = findByPickupRequestId(pickupRequestId);
        state.updateCrewLocation(
                request.lat(),
                request.lng(),
                request.heading() == null ? 0.0 : request.heading(),
                request.speed() == null ? 0.0 : request.speed()
        );

        Long crewId = state.getCrewId() == null ? DEMO_CREW_ID : state.getCrewId();
        CrewGpsState crewState = crewGpsStore.computeIfAbsent(
                crewId,
                id -> new CrewGpsState(id, DEMO_CREW_NAME, request.lat(), request.lng(), "ASSIGNED")
        );
        crewState.lat = request.lat();
        crewState.lng = request.lng();
        crewState.heading = request.heading() == null ? 0.0 : request.heading();
        crewState.speed = request.speed() == null ? 0.0 : request.speed();
        crewState.status = "ASSIGNED";

        appendLocationHistory(
                pickupRequestId,
                request.lat(),
                request.lng(),
                request.heading() == null ? 0.0 : request.heading(),
                request.speed() == null ? 0.0 : request.speed()
        );
        return buildResponse(state);
    }

    public SwapRequestResponse arrive(long pickupRequestId) {
        SwapRequestState state = findByPickupRequestId(pickupRequestId);
        state.arriveCrew();
        return buildResponse(state);
    }

    public SwapRequestResponse completePickup(long pickupRequestId, CrewCompletePickupRequest request) {
        SwapRequestState state = findByPickupRequestId(pickupRequestId);
        state.completePickup(
                request.pickupPhotoFileName(),
                request.hubPhotoFileName(),
                request.inspectionMemo(),
                request.hubMemo()
        );

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
                        valueOrDefault(request.exteriorReason(), "외관 상태를 확인했습니다."),
                        valueOrDefault(request.partsReason(), "재사용 부품 가능성을 확인했습니다."),
                        valueOrDefault(request.materialReason(), "소재 회수 가치를 반영했습니다."),
                        valueOrDefault(request.processingReason(), "수거 및 처리 비용을 반영했습니다.")
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
        return Map.of(
                "message", "Demo pickup state has been reset.",
                "totalSwapRequests", store.size(),
                "availableCrewCalls", getAvailableCalls().size()
        );
    }

    private SwapRequestResponse buildResponse(SwapRequestState state) {
        enrichGpsContext(state);
        return augmentTracking(state, state.toResponse());
    }

    private SwapRequestResponse augmentTracking(SwapRequestState state, SwapRequestResponse response) {
        if (response.tracking() == null) {
            return response;
        }

        long pickupRequestId = response.pickupRequest() == null ? -1L : response.pickupRequest().pickupRequestId();
        List<SwapRequestResponse.LocationHistoryPoint> history = pickupRequestId < 0
                ? List.of()
                : List.copyOf(locationHistoryStore.getOrDefault(pickupRequestId, List.of()));

        SwapRequestResponse.RouteSummary route = resolveRoute(response, history);
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

    private SwapRequestResponse.RouteSummary resolveRoute(
            SwapRequestResponse response,
            List<SwapRequestResponse.LocationHistoryPoint> history
    ) {
        if (response.tracking() == null) {
            return null;
        }

        SwapRequestResponse.DriverLocation driverLocation = response.tracking().driverLocation();
        if (driverLocation == null) {
            return null;
        }

        SwapRequestResponse.RoutePoint origin = new SwapRequestResponse.RoutePoint(driverLocation.lat(), driverLocation.lng());
        SwapRequestResponse.RoutePoint destination = resolveDestination(response);
        if (destination == null) {
            return null;
        }

        SwapRequestResponse.RouteSummary computedRoute = googleRoutesService.computeDrivingRoute(origin, destination);
        List<SwapRequestResponse.RoutePoint> mergedPoints = mergeHistoryWithRoute(history, computedRoute, destination);

        return new SwapRequestResponse.RouteSummary(
                computedRoute.mode(),
                computedRoute.distanceMeters(),
                computedRoute.durationSeconds(),
                computedRoute.distanceLabel(),
                computedRoute.durationLabel(),
                computedRoute.encodedPolyline(),
                mergedPoints,
                computedRoute.calculatedAt()
        );
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

    private List<SwapRequestResponse.RoutePoint> mergeHistoryWithRoute(
            List<SwapRequestResponse.LocationHistoryPoint> history,
            SwapRequestResponse.RouteSummary route,
            SwapRequestResponse.RoutePoint destination
    ) {
        List<SwapRequestResponse.RoutePoint> merged = new ArrayList<>();

        for (SwapRequestResponse.LocationHistoryPoint point : history) {
            merged.add(new SwapRequestResponse.RoutePoint(point.lat(), point.lng()));
        }

        if (route != null && route.points() != null) {
            for (SwapRequestResponse.RoutePoint point : route.points()) {
                if (merged.isEmpty() || !samePoint(merged.get(merged.size() - 1), point)) {
                    merged.add(point);
                }
            }
        }

        if (destination != null && (merged.isEmpty() || !samePoint(merged.get(merged.size() - 1), destination))) {
            merged.add(destination);
        }

        return merged;
    }

    private boolean samePoint(SwapRequestResponse.RoutePoint left, SwapRequestResponse.RoutePoint right) {
        return Math.abs(left.lat() - right.lat()) < 0.00001
                && Math.abs(left.lng() - right.lng()) < 0.00001;
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
            case "REQUESTED", "CONFIRMED" -> "매칭 점수가 높은 크루에게 우선 배차 알림을 발송했습니다.";
            case "ASSIGNED" -> "배정된 크루가 사용자 앱에 실시간 위치를 공유하고 있습니다.";
            case "IN_PROGRESS" -> "크루가 수거지로 이동 중이며 실시간 위치가 갱신되고 있습니다.";
            case "ARRIVED" -> "수거 후 e-waste 공장 이동 준비가 진행 중입니다.";
            case "COMPLETED" -> "e-waste 공장 전달이 완료되었습니다.";
            default -> "예약 또는 바로콜 접수 후 배차 정보가 표시됩니다.";
        };
        String dispatchReason = topCrew == null
                ? "근처 크루 정보가 아직 없습니다."
                : "가까운 크루 거리 " + Math.round(topCrew.distanceMeters()) + "m, 현재 이동 동선, 최근 수락 이력을 반영했습니다.";

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

    private void persistMockInspection(long id, PhotoUploadRequest request) {
        SwapRequestEntity swapRequest = findSwapRequestEntity(id);
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

    private void persistApplianceConfirmation(long id, UpdateApplianceRequest request) {
        SwapRequestEntity swapRequest = findSwapRequestEntity(id);
        ApplianceEntity appliance = applianceRepository.findBySwapRequest_Id(id)
                .orElseGet(() -> applianceRepository.save(ApplianceEntity.create(swapRequest, swapRequest.getApplianceType())));
        appliance.confirmByCustomer(
                valueOrDefault(request.applianceType(), swapRequest.getApplianceType()),
                valueOrDefault(request.brand(), "LG"),
                valueOrDefault(request.modelName(), "Unknown"),
                valueOrDefault(request.estimatedAge(), "확인 필요"),
                valueOrDefault(request.exteriorCondition(), "확인 필요")
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
        enrichGpsContext(state);
        return state.toResponse();
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
        image.ifPresent(applianceImage -> state.applyMockInspection(
                applianceImage.getFileName(),
                swapRequest.getApplianceType(),
                applianceImage.getImageUrl(),
                applianceImage.getFileName(),
                null,
                true
        ));

        applianceRepository.findBySwapRequest_Id(swapRequest.getId()).ifPresent(appliance -> state.updateAppliance(
                appliance.getApplianceType(),
                appliance.getBrand(),
                appliance.getModelName(),
                appliance.getEstimatedAge(),
                appliance.getExteriorCondition()
        ));

        if (SwapRequestStatus.PRE_VALUATION_ACCEPTED.name().equals(swapRequest.getStatus())) {
            state.acceptPreValuation();
        }

        pickupRequestRepository.findFirstBySwapRequest_IdOrderByCreatedAtDesc(swapRequest.getId())
                .ifPresent(pickupRequest -> state.restorePickup(
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
                ));

        store.put(state.getId(), state);
        return state;
    }

    private SwapRequestEntity findSwapRequestEntity(long id) {
        return swapRequestRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Swap request not found in DB: " + id));
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
        crewGpsStore.put(101L, new CrewGpsState(101L, DEMO_CREW_NAME, 37.5665, 126.9780, "AVAILABLE"));
        crewGpsStore.put(102L, new CrewGpsState(102L, "서교 크루", 37.5563, 126.9220, "AVAILABLE"));
        crewGpsStore.put(103L, new CrewGpsState(103L, "강서 크루", 37.5585, 126.8321, "AVAILABLE"));
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
}
