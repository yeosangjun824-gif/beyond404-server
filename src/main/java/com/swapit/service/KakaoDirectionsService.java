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
public class KakaoDirectionsService {
    private static final String DIRECTIONS_ENDPOINT = "https://apis-navi.kakaomobility.com/v1/directions";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${kakao.mobility.rest-api-key:}")
    private String restApiKey;

    public SwapRequestResponse.RouteSummary computeDrivingRoute(
            SwapRequestResponse.RoutePoint origin,
            SwapRequestResponse.RoutePoint destination
    ) {
        if (origin == null || destination == null) {
            return null;
        }

        if (restApiKey == null || restApiKey.isBlank()) {
            log.info("Kakao Mobility REST API key is not configured. Road route will not be drawn.");
            return null;
        }

        try {
            URI requestUri = URI.create(DIRECTIONS_ENDPOINT
                    + "?origin=" + formatCoordinate(origin.lng(), origin.lat())
                    + "&destination=" + formatCoordinate(destination.lng(), destination.lat())
                    + "&summary=false");

            HttpRequest request = HttpRequest.newBuilder(requestUri)
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Kakao Mobility Directions API failed with status {} and body {}", response.statusCode(), response.body());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode routeNode = root.path("routes").isArray() && !root.path("routes").isEmpty()
                    ? root.path("routes").get(0)
                    : null;

            if (routeNode == null || routeNode.isMissingNode()) {
                log.warn("Kakao Mobility Directions API returned no routes for origin {} and destination {}", origin, destination);
                return null;
            }

            JsonNode summary = routeNode.path("summary");
            double distanceMeters = summary.path("distance").asDouble(haversineMeters(origin, destination));
            long durationSeconds = summary.path("duration").asLong(Math.max(60L, Math.round(distanceMeters / 7.2)));
            List<SwapRequestResponse.RoutePoint> points = parseRoutePoints(routeNode);

            if (points.isEmpty()) {
                log.warn("Kakao Mobility Directions API returned a route without vertexes for origin {} and destination {}", origin, destination);
                return null;
            }

            return new SwapRequestResponse.RouteSummary(
                    "DRIVE",
                    round(distanceMeters),
                    durationSeconds,
                    formatDistance(distanceMeters),
                    formatDuration(durationSeconds),
                    null,
                    points,
                    LocalDateTime.now()
            );
        } catch (IOException | InterruptedException | IllegalArgumentException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Failed to compute Kakao route. Road route will not be drawn.", error);
            return null;
        }
    }

    private List<SwapRequestResponse.RoutePoint> parseRoutePoints(JsonNode routeNode) {
        List<SwapRequestResponse.RoutePoint> points = new ArrayList<>();
        JsonNode sections = routeNode.path("sections");
        if (!sections.isArray()) {
            return points;
        }

        for (JsonNode section : sections) {
            JsonNode roads = section.path("roads");
            if (!roads.isArray()) {
                continue;
            }

            for (JsonNode road : roads) {
                JsonNode vertexes = road.path("vertexes");
                if (!vertexes.isArray()) {
                    continue;
                }

                for (int index = 0; index + 1 < vertexes.size(); index += 2) {
                    double lng = vertexes.get(index).asDouble();
                    double lat = vertexes.get(index + 1).asDouble();
                    points.add(new SwapRequestResponse.RoutePoint(lat, lng));
                }
            }
        }

        return points;
    }

    private String formatCoordinate(double lng, double lat) {
        return String.format(Locale.US, "%.7f,%.7f", lng, lat);
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
}
