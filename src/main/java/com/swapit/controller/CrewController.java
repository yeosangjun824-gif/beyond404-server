package com.swapit.controller;

import com.swapit.dto.CrewCompletePickupRequest;
import com.swapit.dto.CrewLocationRequest;
import com.swapit.dto.SwapRequestResponse;
import com.swapit.service.SwapRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/crew")
@RequiredArgsConstructor
public class CrewController {
    private final SwapRequestService swapRequestService;

    @PostMapping("/login/mock")
    public Map<String, Object> login() {
        return Map.of(
                "crewId", 101,
                "name", "Aarav Sharma",
                "certificationStatus", "APPROVED",
                "region", "New Delhi"
        );
    }

    @GetMapping("/calls")
    public List<SwapRequestResponse> getAvailableCalls() {
        return swapRequestService.getAvailableCalls();
    }

    @GetMapping("/calls/pending")
    public List<SwapRequestResponse> getPendingCalls() {
        return swapRequestService.getPendingCalls();
    }

    @GetMapping("/calls/active")
    public List<SwapRequestResponse> getActiveCalls() {
        return swapRequestService.getActiveCalls();
    }

    @GetMapping("/calls/{pickupRequestId}")
    public SwapRequestResponse getCallDetail(@PathVariable long pickupRequestId) {
        return swapRequestService.getCrewCallDetail(pickupRequestId);
    }

    @GetMapping("/pickups/{pickupRequestId}/location-history")
    public List<SwapRequestResponse.LocationHistoryPoint> getLocationHistory(@PathVariable long pickupRequestId) {
        return swapRequestService.getLocationHistory(pickupRequestId);
    }

    @PostMapping("/calls/{pickupRequestId}/accept")
    public SwapRequestResponse acceptCall(@PathVariable long pickupRequestId) {
        return swapRequestService.acceptCall(pickupRequestId);
    }

    @PostMapping("/pickups/{pickupRequestId}/depart")
    public SwapRequestResponse depart(@PathVariable long pickupRequestId) {
        return swapRequestService.depart(pickupRequestId);
    }

    @PostMapping("/pickups/{pickupRequestId}/location")
    public SwapRequestResponse updateLocation(
            @PathVariable long pickupRequestId,
            @Valid @RequestBody CrewLocationRequest request
    ) {
        return swapRequestService.updateLocation(pickupRequestId, request);
    }

    @PostMapping("/pickups/{pickupRequestId}/arrive")
    public SwapRequestResponse arrive(@PathVariable long pickupRequestId) {
        return swapRequestService.arrive(pickupRequestId);
    }

    @PostMapping("/pickups/{pickupRequestId}/complete")
    public SwapRequestResponse completePickup(
            @PathVariable long pickupRequestId,
            @RequestBody CrewCompletePickupRequest request
    ) {
        return swapRequestService.completePickup(pickupRequestId, request);
    }
}
