package com.swapit.service;

import com.swapit.domain.SwapRequestState;
import com.swapit.dto.BookingRequest;
import com.swapit.dto.CreateSwapRequestRequest;
import com.swapit.dto.PhotoUploadRequest;
import com.swapit.dto.SwapRequestResponse;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SwapRequestService {
    private final AtomicLong sequence = new AtomicLong(1);
    private final Map<Long, SwapRequestState> store = new ConcurrentHashMap<>();

    public SwapRequestResponse create(CreateSwapRequestRequest request) {
        long id = sequence.getAndIncrement();
        SwapRequestState state = new SwapRequestState(id);
        store.put(id, state);
        return state.toResponse();
    }

    public SwapRequestResponse analyzePhoto(long id, PhotoUploadRequest request) {
        SwapRequestState state = findState(id);
        state.applyMockInspection(request.fileName());
        return state.toResponse();
    }

    public SwapRequestResponse confirmBooking(long id, BookingRequest request) {
        SwapRequestState state = findState(id);
        state.confirmBooking(request.bookingDate(), request.bookingTime(), request.address());
        return state.toResponse();
    }

    public SwapRequestResponse completeMockFinalValuation(long id) {
        SwapRequestState state = findState(id);
        state.completeMockFinalValuation();
        return state.toResponse();
    }

    public SwapRequestResponse get(long id) {
        return findState(id).toResponse();
    }

    private SwapRequestState findState(long id) {
        SwapRequestState state = store.get(id);
        if (state == null) {
            throw new NoSuchElementException("Swap request not found: " + id);
        }
        return state;
    }
}

