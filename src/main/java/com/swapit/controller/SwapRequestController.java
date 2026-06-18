package com.swapit.controller;

import com.swapit.dto.BookingRequest;
import com.swapit.dto.BookingAvailabilityResponse;
import com.swapit.dto.CrewReviewRequest;
import com.swapit.dto.CreateInstantCallRequest;
import com.swapit.dto.CreateSwapRequestRequest;
import com.swapit.dto.InstantCallRequest;
import com.swapit.dto.PhotoUploadRequest;
import com.swapit.dto.ReReviewRequest;
import com.swapit.dto.SelectReplacementProductRequest;
import com.swapit.dto.SwapRequestResponse;
import com.swapit.dto.UpdateApplianceRequest;
import com.swapit.service.SwapRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/swap-requests")
@RequiredArgsConstructor
public class SwapRequestController {
    private final SwapRequestService swapRequestService;

    @PostMapping
    public SwapRequestResponse create(@Valid @RequestBody CreateSwapRequestRequest request) {
        return swapRequestService.create(request);
    }

    @PostMapping("/instant-call")
    public SwapRequestResponse createInstantCall(@Valid @RequestBody CreateInstantCallRequest request) {
        return swapRequestService.createInstantCall(request);
    }

    @GetMapping("/latest")
    public ResponseEntity<SwapRequestResponse> getLatestByUser(@RequestParam long userId) {
        return swapRequestService.getLatestByUser(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/booking-availability")
    public BookingAvailabilityResponse getBookingAvailability(@RequestParam LocalDate date) {
        return swapRequestService.getBookingAvailability(date);
    }

    @PostMapping("/{id}/photos")
    public SwapRequestResponse analyzePhoto(
            @PathVariable long id,
            @Valid @RequestBody PhotoUploadRequest request
    ) {
        return swapRequestService.analyzePhoto(id, request);
    }

    @PatchMapping("/{id}/appliance")
    public SwapRequestResponse updateAppliance(
            @PathVariable long id,
            @RequestBody UpdateApplianceRequest request
    ) {
        return swapRequestService.updateAppliance(id, request);
    }

    @PostMapping("/{id}/pre-valuation/accept")
    public SwapRequestResponse acceptPreValuation(@PathVariable long id) {
        return swapRequestService.acceptPreValuation(id);
    }

    @PostMapping("/{id}/replacement-product")
    public SwapRequestResponse selectReplacementProduct(
            @PathVariable long id,
            @Valid @RequestBody SelectReplacementProductRequest request
    ) {
        return swapRequestService.selectReplacementProduct(id, request);
    }

    @PostMapping("/{id}/booking")
    public SwapRequestResponse confirmBooking(
            @PathVariable long id,
            @Valid @RequestBody BookingRequest request
    ) {
        return swapRequestService.confirmBooking(id, request);
    }

    @PostMapping("/{id}/instant-call")
    public SwapRequestResponse requestInstantCall(
            @PathVariable long id,
            @Valid @RequestBody InstantCallRequest request
    ) {
        return swapRequestService.requestInstantCall(id, request);
    }

    @PostMapping("/{id}/cancel")
    public SwapRequestResponse cancel(@PathVariable long id) {
        return swapRequestService.cancel(id);
    }

    @GetMapping("/{id}/tracking")
    public SwapRequestResponse getTracking(@PathVariable long id) {
        return swapRequestService.getTracking(id);
    }

    @PostMapping("/{id}/final-valuation/mock")
    public SwapRequestResponse completeMockFinalValuation(@PathVariable long id) {
        return swapRequestService.completeMockFinalValuation(id);
    }

    @PostMapping("/{id}/re-review")
    public SwapRequestResponse requestReReview(
            @PathVariable long id,
            @Valid @RequestBody ReReviewRequest request
    ) {
        return swapRequestService.requestReReview(id, request);
    }

    @PostMapping("/{id}/re-review/mock-complete")
    public SwapRequestResponse completeMockReReview(@PathVariable long id) {
        return swapRequestService.completeMockReReview(id);
    }

    @PostMapping("/{id}/credits")
    public SwapRequestResponse issueCredit(@PathVariable long id) {
        return swapRequestService.issueCredit(id);
    }

    @PostMapping("/{id}/delivery/mock-progress")
    public SwapRequestResponse advanceDeliveryTracking(@PathVariable long id) {
        return swapRequestService.advanceDeliveryTracking(id);
    }

    @PostMapping("/{id}/crew-review")
    public SwapRequestResponse submitCrewReview(
            @PathVariable long id,
            @Valid @RequestBody CrewReviewRequest request
    ) {
        return swapRequestService.submitCrewReview(id, request);
    }

    @GetMapping("/{id}")
    public SwapRequestResponse get(@PathVariable long id) {
        return swapRequestService.get(id);
    }
}