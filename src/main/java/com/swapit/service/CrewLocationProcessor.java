package com.swapit.service;

import com.swapit.dto.CrewLocationRequest;
import com.swapit.dto.SwapRequestResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CrewLocationProcessor {
    private static final double DEFAULT_ACCURACY_METERS = 35.0;
    private static final double MAX_ACCEPTABLE_ACCURACY_METERS = 150.0;
    private static final double ACCURACY_WEIGHT_CAP_METERS = 80.0;
    private static final long MAX_LOCATION_AGE_MILLIS = 60_000L;
    private static final long HISTORY_WINDOW_MILLIS = 15_000L;
    private static final int MAX_HISTORY_SIZE = 5;
    private static final double MAX_REASONABLE_SPEED_MPS = 8.0;
    private static final double MIN_JUMP_ALLOWANCE_METERS = 18.0;
    private static final double ROUTE_SNAP_DISTANCE_METERS = 30.0;

    private final Map<String, Deque<LocationSample>> histories = new ConcurrentHashMap<>();
    private final Map<String, ProcessedLocation> smoothedLocations = new ConcurrentHashMap<>();

    public ProcessedLocation process(
            long pickupRequestId,
            long crewId,
            CrewLocationRequest request,
            List<SwapRequestResponse.RoutePoint> routePoints
    ) {
        if (request == null || request.lat() == null || request.lng() == null) {
            return ProcessedLocation.rejected();
        }

        double rawLat = request.lat();
        double rawLng = request.lng();
        if (!isValidCoordinate(rawLat, rawLng)) {
            return ProcessedLocation.rejected();
        }

        double reportedAccuracy = request.accuracy() == null ? DEFAULT_ACCURACY_METERS : request.accuracy();
        if (reportedAccuracy < 0 || reportedAccuracy > MAX_ACCEPTABLE_ACCURACY_METERS) {
            return ProcessedLocation.rejected();
        }

        double accuracy = Math.min(reportedAccuracy, ACCURACY_WEIGHT_CAP_METERS);
        if (accuracy < 0 || accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) {
            return ProcessedLocation.rejected();
        }

        long now = System.currentTimeMillis();
        long capturedAt = request.capturedAt() == null ? now : request.capturedAt();
        if (Math.abs(now - capturedAt) > MAX_LOCATION_AGE_MILLIS) {
            return ProcessedLocation.rejected();
        }

        String key = pickupRequestId + ":" + crewId;
        LocationSample current = new LocationSample(rawLat, rawLng, accuracy, capturedAt);
        Deque<LocationSample> history = histories.computeIfAbsent(key, ignored -> new ArrayDeque<>());

        LocationSample averaged;
        synchronized (history) {
            LocationSample previous = history.peekLast();
            if (isUnrealisticJump(previous, current)) {
                return ProcessedLocation.rejected();
            }

            history.addLast(current);
            trimHistory(history, capturedAt);
            averaged = weightedAverage(history);
        }

        ProcessedLocation smoothed = smooth(key, averaged, request);
        SnapResult snapped = snapToRoute(smoothed.lat(), smoothed.lng(), routePoints);
        if (snapped != null) {
            smoothed = smoothed.withCoordinate(snapped.lat(), snapped.lng());
            smoothedLocations.put(key, smoothed);
        }

        return smoothed;
    }

    private boolean isValidCoordinate(double lat, double lng) {
        return !Double.isNaN(lat)
                && !Double.isNaN(lng)
                && lat >= -90
                && lat <= 90
                && lng >= -180
                && lng <= 180;
    }

    private boolean isUnrealisticJump(LocationSample previous, LocationSample current) {
        if (previous == null) {
            return false;
        }

        long elapsedMillis = current.capturedAt() - previous.capturedAt();
        if (elapsedMillis <= 0 || elapsedMillis > HISTORY_WINDOW_MILLIS * 2) {
            return false;
        }

        double elapsedSeconds = Math.max(1.0, elapsedMillis / 1000.0);
        double distance = distanceMeters(previous.lat(), previous.lng(), current.lat(), current.lng());
        double allowance = Math.max(
                MIN_JUMP_ALLOWANCE_METERS,
                MAX_REASONABLE_SPEED_MPS * elapsedSeconds + current.accuracy() * 0.8
        );

        return distance > allowance;
    }

    private void trimHistory(Deque<LocationSample> history, long capturedAt) {
        while (!history.isEmpty() && capturedAt - history.peekFirst().capturedAt() > HISTORY_WINDOW_MILLIS) {
            history.removeFirst();
        }

        while (history.size() > MAX_HISTORY_SIZE) {
            history.removeFirst();
        }
    }

    private LocationSample weightedAverage(Deque<LocationSample> history) {
        double weightedLat = 0.0;
        double weightedLng = 0.0;
        double totalWeight = 0.0;
        double bestAccuracy = Double.MAX_VALUE;
        long latestCapturedAt = 0L;

        for (LocationSample sample : history) {
            double weight = 1.0 / Math.max(sample.accuracy(), 5.0);
            weightedLat += sample.lat() * weight;
            weightedLng += sample.lng() * weight;
            totalWeight += weight;
            bestAccuracy = Math.min(bestAccuracy, sample.accuracy());
            latestCapturedAt = Math.max(latestCapturedAt, sample.capturedAt());
        }

        if (totalWeight == 0.0) {
            LocationSample latest = history.peekLast();
            return latest == null ? new LocationSample(0, 0, DEFAULT_ACCURACY_METERS, latestCapturedAt) : latest;
        }

        return new LocationSample(
                weightedLat / totalWeight,
                weightedLng / totalWeight,
                bestAccuracy == Double.MAX_VALUE ? DEFAULT_ACCURACY_METERS : bestAccuracy,
                latestCapturedAt
        );
    }

    private ProcessedLocation smooth(String key, LocationSample averaged, CrewLocationRequest request) {
        ProcessedLocation previous = smoothedLocations.get(key);
        double heading = request.heading() == null ? 0.0 : request.heading();
        double speed = request.speed() == null ? 0.0 : request.speed();
        double alpha = smoothingAlpha(averaged.accuracy());

        ProcessedLocation next;
        if (previous == null) {
            next = new ProcessedLocation(
                    true,
                    averaged.lat(),
                    averaged.lng(),
                    heading,
                    speed,
                    averaged.accuracy(),
                    averaged.capturedAt()
            );
        } else {
            next = new ProcessedLocation(
                    true,
                    previous.lat() + (averaged.lat() - previous.lat()) * alpha,
                    previous.lng() + (averaged.lng() - previous.lng()) * alpha,
                    heading,
                    speed,
                    averaged.accuracy(),
                    averaged.capturedAt()
            );
        }

        smoothedLocations.put(key, next);
        return next;
    }

    private double smoothingAlpha(double accuracy) {
        if (accuracy <= 10.0) {
            return 0.75;
        }
        if (accuracy <= 25.0) {
            return 0.55;
        }
        return 0.35;
    }

    private SnapResult snapToRoute(double lat, double lng, List<SwapRequestResponse.RoutePoint> routePoints) {
        if (routePoints == null || routePoints.size() < 2) {
            return null;
        }

        SnapResult best = null;
        for (int index = 0; index < routePoints.size() - 1; index += 1) {
            SwapRequestResponse.RoutePoint start = routePoints.get(index);
            SwapRequestResponse.RoutePoint end = routePoints.get(index + 1);
            SnapResult candidate = closestPointOnSegment(lat, lng, start, end);
            if (candidate != null && (best == null || candidate.distanceMeters() < best.distanceMeters())) {
                best = candidate;
            }
        }

        if (best == null || best.distanceMeters() > ROUTE_SNAP_DISTANCE_METERS) {
            return null;
        }

        return best;
    }

    private SnapResult closestPointOnSegment(
            double lat,
            double lng,
            SwapRequestResponse.RoutePoint start,
            SwapRequestResponse.RoutePoint end
    ) {
        double[] origin = project(lat, lng, lat, lng);
        double[] a = project(start.lat(), start.lng(), lat, lng);
        double[] b = project(end.lat(), end.lng(), lat, lng);
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0.0) {
            double distance = distanceMeters(lat, lng, start.lat(), start.lng());
            return new SnapResult(start.lat(), start.lng(), distance);
        }

        double t = ((origin[0] - a[0]) * dx + (origin[1] - a[1]) * dy) / lengthSquared;
        double clamped = Math.max(0.0, Math.min(1.0, t));
        double snappedLat = start.lat() + (end.lat() - start.lat()) * clamped;
        double snappedLng = start.lng() + (end.lng() - start.lng()) * clamped;
        double distance = distanceMeters(lat, lng, snappedLat, snappedLng);
        return new SnapResult(snappedLat, snappedLng, distance);
    }

    private double[] project(double lat, double lng, double refLat, double refLng) {
        double metersPerDegree = 111_320.0;
        double x = (lng - refLng) * Math.cos(Math.toRadians(refLat)) * metersPerDegree;
        double y = (lat - refLat) * metersPerDegree;
        return new double[] { x, y };
    }

    private double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double earthRadius = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private record LocationSample(
            double lat,
            double lng,
            double accuracy,
            long capturedAt
    ) {
    }

    private record SnapResult(
            double lat,
            double lng,
            double distanceMeters
    ) {
    }

    public record ProcessedLocation(
            boolean accepted,
            double lat,
            double lng,
            double heading,
            double speed,
            Double accuracy,
            long capturedAt
    ) {
        private static ProcessedLocation rejected() {
            return new ProcessedLocation(false, 0.0, 0.0, 0.0, 0.0, null, 0L);
        }

        private ProcessedLocation withCoordinate(double lat, double lng) {
            return new ProcessedLocation(accepted, lat, lng, heading, speed, accuracy, capturedAt);
        }
    }
}
