package com.swapit.controller;

import com.swapit.dto.BookingRequest;
import com.swapit.dto.CreateSwapRequestRequest;
import com.swapit.dto.PhotoUploadRequest;
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

@RestController
@RequestMapping("/api/swap-requests")
@RequiredArgsConstructor
public class SwapRequestController {
    private final SwapRequestService swapRequestService;

    @PostMapping
    public SwapRequestResponse create(@Valid @RequestBody CreateSwapRequestRequest request) {
        return swapRequestService.create(request);
    }

    @PostMapping("/{id}/photos")
    public SwapRequestResponse analyzePhoto(
            @PathVariable long id,
            @Valid @RequestBody PhotoUploadRequest request
    ) {
        return swapRequestService.analyzePhoto(id, request);
    }

    @PostMapping("/{id}/booking")
    public SwapRequestResponse confirmBooking(
            @PathVariable long id,
            @Valid @RequestBody BookingRequest request
    ) {
        return swapRequestService.confirmBooking(id, request);
    }

    @PostMapping("/{id}/final-valuation/mock")
    public SwapRequestResponse completeMockFinalValuation(@PathVariable long id) {
        return swapRequestService.completeMockFinalValuation(id);
    }

    @GetMapping("/{id}")
    public SwapRequestResponse get(@PathVariable long id) {
        return swapRequestService.get(id);
    }
}

