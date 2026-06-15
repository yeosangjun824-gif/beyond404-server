package com.swapit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swapit.dto.SwapRequestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleRoutesService {
    private static final URI ROUTES_ENDPOINT = URI.create("https://routes.googleapis.com/directions/v2:computeRoutes");
    private static final String ROUTE_FIELD_MASK = "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${google.maps.server-api-key:}")
    private String apiKey;

    public SwapRequestResponse.RouteSummary computeDrivingRoute(
            SwapRequestResponse.RoutePoint origin,
            SwapRequestResponse.RoutePoint destination
    ) {
        if (origin == null || destination == null) {
            return null;
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.info("Google Routes API key is not configured. Falling back to straight-line route.");
            return fallbackRoute(origin, destination);
        }

        try {
            String requestBody = objectMapper.writeValueAsString(new ComputeRoutesRequest(origin, destination));
            HttpRequest request = HttpRequest.newBuilder(ROUTES_ENDPOINT)
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", ROUTE_FIELD_MASK)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Google Routes API failed with status {} and body {}", response.statusCode(), response.body());
                return fallbackRoute(origin, destination);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode routeNode = root.path("routes").isArray() && !root.path("routes").isEmpty()
                    ? root.path("routes").get(0)
                    : null;

            if (routeNode == null || routeNode.isMissingNode()) {
                log.warn("Google Routes API returned no routes for origin {} and destination {}", origin, destination);
                return fallbackRoute(origin, destination);
            }

            double distanceMeters = routeNode.path("distanceMeters").asDouble(haversineMeters(origin, destination));
            long durationSeconds = parseDurationSeconds(routeNode.path("duration").asText());
            String encodedPolyline = routeNode.path("polyline").path("encodedPolyline").asText("");
            List<SwapRequestResponse.RoutePoint> points = encodedPolyline.isBlank()
                    ? List.of(origin, destination)
                    : decodePolyline(encodedPolyline);

            return new SwapRequestResponse.RouteSummary(
                    "DRIVE",
                    round(distanceMeters),
                    durationSeconds,
                    formatDistance(distanceMeters),
                    formatDuration(durationSeconds),
                    encodedPolyline.isBlank() ? null : encodedPolyline,
                    points,
                    LocalDateTime.now()
            );
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Failed to compute Google route. Falling back to straight-line route.", error);
            return fallbackRoute(origin, destination);
        }
    }

    private SwapRequestResponse.RouteSummary fallbackRoute(
            SwapRequestResponse.RoutePoint origin,
            SwapRequestResponse.RoutePoint destination
    ) {
        double distanceMeters = haversineMeters(origin, destination);
        long durationSeconds = Math.max(60L, Math.round(distanceMeters / 7.2));

        return new SwapRequestResponse.RouteSummary(
                "FALLBACK_DRIVE",
                round(distanceMeters),
                durationSeconds,
                formatDistance(distanceMeters),
                formatDuration(durationSeconds),
                null,
                List.of(origin, destination),
                LocalDateTime.now()
        );
    }

    private long parseDurationSeconds(String durationText) {
        if (durationText == null || durationText.isBlank()) {
            return 0L;
        }

        if (durationText.endsWith("s")) {
            String normalized = durationText.substring(0, durationText.length() - 1);
            int dotIndex = normalized.indexOf('.');
            if (dotIndex >= 0) {
                normalized = normalized.substring(0, dotIndex);
            }
            try {
                return Long.parseLong(normalized);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }

        return 0L;
    }

    private List<SwapRequestResponse.RoutePoint> decodePolyline(String encoded) {
        List<SwapRequestResponse.RoutePoint> path = new ArrayList<>();
        int index = 0;
        int lat = 0;
        int lng = 0;

        while (index < encoded.length()) {
            int result = 0;
            int shift = 0;
            int b;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            lat += (result & 1) != 0 ? ~(result >> 1) : result >> 1;

            result = 0;
            shift = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            lng += (result & 1) != 0 ? ~(result >> 1) : result >> 1;

            path.add(new SwapRequestResponse.RoutePoint(lat / 1e5, lng / 1e5));
        }

        return path;
    }

    private double haversineMeters(SwapRequestResponse.RoutePoint origin, SwapRequestResponse.RoutePoint destination) {
        double earthRadius = 6371000.0;
        double dLat = Math.toRadians(destination.lat() - origin.lat());
        double dLng = Math.toRadians(destination.lng() - origin.lng());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(origin.lat())) * Math.cos(Math.toRadians(destination.lat()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String formatDistance(double distanceMeters) {
        if (distanceMeters >= 1000) {
            return String.format(Locale.US, "%.1f km", distanceMeters / 1000.0);
        }
        return Math.round(distanceMeters) + " m";
    }

    private String formatDuration(long durationSeconds) {
        long minutes = Math.max(1L, Math.round(durationSeconds / 60.0));
        if (minutes >= 60) {
            long hours = minutes / 60;
            long remainder = minutes % 60;
            if (remainder == 0) {
                return hours + " hr";
            }
            return hours + " hr " + remainder + " min";
        }
        return minutes + " min";
    }

    private record ComputeRoutesRequest(
            Waypoint origin,
            Waypoint destination,
            String travelMode,
            String routingPreference,
            String polylineQuality,
            String polylineEncoding
    ) {
        private ComputeRoutesRequest(SwapRequestResponse.RoutePoint origin, SwapRequestResponse.RoutePoint destination) {
            this(
                    new Waypoint(new RouteLocation(new LatLng(origin.lat(), origin.lng()))),
                    new Waypoint(new RouteLocation(new LatLng(destination.lat(), destination.lng()))),
                    "DRIVE",
                    "TRAFFIC_AWARE_OPTIMAL",
                    "OVERVIEW",
                    "ENCODED_POLYLINE"
            );
        }
    }

    private record Waypoint(RouteLocation location) {
    }

    private record RouteLocation(LatLng latLng) {
    }

    private record LatLng(double latitude, double longitude) {
    }
}
