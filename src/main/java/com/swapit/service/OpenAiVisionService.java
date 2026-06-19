package com.swapit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiVisionService {
    private static final String CHAT_COMPLETIONS_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.vision-model:gpt-4o}")
    private String visionModel;

    public Optional<OpenAiVisionResult> identifyAppliance(String imageReference, String applianceType) {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("OPENAI_API_KEY is not configured. Appliance identification will remain unknown.");
            return Optional.empty();
        }

        try {
            String content = callVision(imageReference, buildLabelPrompt(applianceType), "appliance label");
            OpenAiVisionResult result = parseResult(content);
            log.info(
                    "OpenAI Vision label result: brand={}, modelName={}, applianceType={}",
                    safeLogValue(result.brand()),
                    safeLogValue(result.modelName()),
                    safeLogValue(result.applianceType())
            );
            return Optional.of(result);
        } catch (IOException | InterruptedException | IllegalArgumentException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Failed to identify appliance with OpenAI Vision. Appliance identification will remain unknown.", error);
            return Optional.empty();
        }
    }

    public Optional<OpenAiConditionResult> analyzeCondition(String imageReference, String applianceType) {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("OPENAI_API_KEY is not configured. Appliance condition analysis will remain unchanged.");
            return Optional.empty();
        }

        try {
            String content = callVision(imageReference, buildConditionPrompt(applianceType), "appliance exterior");
            return Optional.of(parseConditionResult(content));
        } catch (IOException | InterruptedException | IllegalArgumentException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Failed to analyze appliance condition with OpenAI Vision. Appliance condition will remain unchanged.", error);
            return Optional.empty();
        }
    }

    private String callVision(String imageReference, String prompt, String context) throws IOException, InterruptedException {
        Optional<String> resolvedImageUrl = resolveVisionImageUrl(imageReference);
        if (resolvedImageUrl.isEmpty()) {
            throw new IllegalArgumentException("No readable image URL or local image file was provided for " + context + ".");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(CHAT_COMPLETIONS_ENDPOINT))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(resolvedImageUrl.get(), prompt)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("OpenAI Vision API failed for {} with status {} and body {}", context, response.statusCode(), response.body());
            throw new IllegalArgumentException("OpenAI Vision API failed with status " + response.statusCode());
        }

        return objectMapper.readTree(response.body())
                .path("choices")
                .path(0)
                .path("message")
                .path("content")
                .asText("");
    }

    private String buildRequestBody(String imageUrl, String prompt) throws IOException {
        Map<String, Object> textContent = new LinkedHashMap<>();
        textContent.put("type", "text");
        textContent.put("text", prompt);

        Map<String, Object> imageUrlContent = new LinkedHashMap<>();
        imageUrlContent.put("type", "image_url");
        imageUrlContent.put("image_url", Map.of(
                "url", imageUrl,
                "detail", "high"
        ));

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", List.of(textContent, imageUrlContent));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", valueOrDefault(visionModel, DEFAULT_MODEL));
        body.put("messages", List.of(message));
        body.put("max_tokens", 200);
        body.put("temperature", 0);
        body.put("response_format", Map.of("type", "json_object"));

        return objectMapper.writeValueAsString(body);
    }

    private String buildLabelPrompt(String applianceType) {
        return """
                This is a label or sticker photo attached to an appliance.
                Read the printed/OCR text carefully and reply only with valid JSON:
                {"brand":"brand name or null","modelName":"exact model code using visible letters, numbers, hyphens or slashes only or null","applianceType":"washing_machine | refrigerator | air_conditioner | microwave | tv | null"}
                Appliance type hint: %s.
                Prefer fields labelled model, model name, model no, model code, 모델명, 형명, 품명, or 제품명.
                Do not invent model codes. If unreadable, return null for modelName.
                """.formatted(valueOrDefault(applianceType, "unknown"));
    }

    private String buildConditionPrompt(String applianceType) {
        return """
                This is an exterior photo of an appliance.
                Analyze only the visible exterior condition and reply only with valid JSON:
                {"estimatedAge":"0-1년 | 1-3년 | 2-4년 | 4-6년 | 6년 이상 중 하나","exteriorCondition":"외관 상태를 한국어 한 문장으로 설명"}
                Appliance type hint: %s.
                If age cannot be estimated, return null for estimatedAge.
                """.formatted(valueOrDefault(applianceType, "unknown"));
    }

    OpenAiVisionResult parseResult(String content) throws IOException {
        String json = extractJsonObject(content);
        JsonNode root = objectMapper.readTree(json);
        String brand = firstJsonText(root, "brand", "manufacturer", "maker", "company");
        String modelName = firstJsonText(
                root,
                "modelName",
                "model_name",
                "model",
                "modelNo",
                "model_no",
                "modelNumber",
                "model_number",
                "modelCode",
                "model_code",
                "productModel",
                "product_model"
        );
        String applianceType = firstJsonText(root, "applianceType", "appliance_type", "type", "category");
        return new OpenAiVisionResult(
                valueOrDefault(brand, "unknown"),
                valueOrDefault(modelName, "unknown"),
                valueOrDefault(applianceType, null)
        );
    }

    OpenAiConditionResult parseConditionResult(String content) throws IOException {
        String json = extractJsonObject(content);
        JsonNode root = objectMapper.readTree(json);
        String estimatedAge = normalizeResultField(root.path("estimatedAge").asText(""));
        String exteriorCondition = normalizeResultField(root.path("exteriorCondition").asText(""));
        return new OpenAiConditionResult(
                valueOrDefault(estimatedAge, null),
                valueOrDefault(exteriorCondition, null)
        );
    }

    Optional<String> resolveVisionImageUrl(String imageReference) throws IOException {
        if (imageReference == null || imageReference.isBlank()) {
            return Optional.empty();
        }

        String trimmed = imageReference.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("data:image/")) {
            return Optional.of(trimmed);
        }

        Path imagePath = trimmed.startsWith("file:")
                ? Path.of(URI.create(trimmed))
                : Path.of(trimmed);

        if (!Files.isRegularFile(imagePath)) {
            return Optional.empty();
        }

        String mimeType = Files.probeContentType(imagePath);
        if (mimeType == null || !mimeType.startsWith("image/")) {
            mimeType = "image/jpeg";
        }

        String encoded = Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
        return Optional.of("data:" + mimeType + ";base64," + encoded);
    }

    private String extractJsonObject(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("OpenAI response content is empty.");
        }

        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("OpenAI response did not contain a JSON object.");
        }

        return content.substring(start, end + 1);
    }

    private String normalizeResultField(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if ("null".equalsIgnoreCase(trimmed) || "unknown".equalsIgnoreCase(trimmed) || "n/a".equalsIgnoreCase(trimmed)) {
            return "";
        }
        return trimmed;
    }

    private String firstJsonText(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = normalizeResultField(root.path(fieldName).asText(""));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String safeLogValue(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record OpenAiVisionResult(String brand, String modelName, String applianceType) {
        public static OpenAiVisionResult unknown() {
            return new OpenAiVisionResult("unknown", "unknown", null);
        }
    }

    public record OpenAiConditionResult(String estimatedAge, String exteriorCondition) {
        public static OpenAiConditionResult empty() {
            return new OpenAiConditionResult(null, null);
        }
    }
}
