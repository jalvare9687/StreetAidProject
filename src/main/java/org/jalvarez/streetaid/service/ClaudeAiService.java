package org.jalvarez.streetaid.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jalvarez.streetaid.model.FoodResource;
import org.jalvarez.streetaid.model.LookupResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClaudeAiService {

    @Value("${anthropic.api.key}")
    private String apiKey;

    @Value("${anthropic.api.url}")
    private String apiUrl;

    @Value("${anthropic.model}")
    private String model;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ClaudeAiService() {
        this.webClient = WebClient.create();
        this.objectMapper = new ObjectMapper();
    }

    public ClaudeResponse generateSummary(LookupResponse data) {
        try {
            String prompt = buildPrompt(data);

            // Build request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("max_tokens", 600);

            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode message = messages.addObject();
            message.put("role", "user");
            message.put("content", prompt);

            // Call Claude API
            String response = webClient.post()
                    .uri(apiUrl)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Parse response
            JsonNode root = objectMapper.readTree(response);
            String fullText = root.path("content")
                    .get(0)
                    .path("text")
                    .asText();

            // We ask Claude to split web and SMS with [SMS] divider
            String[] parts = fullText.split("\\[SMS\\]");
            String webSummary = parts[0].trim();
            String smsSummary = parts.length > 1
                    ? parts[1].trim()
                    : buildFallbackSms(data);

            return new ClaudeResponse(webSummary, smsSummary);

        } catch (Exception e) {
            System.err.println("Claude API error: " + e.getMessage());
            return new ClaudeResponse(
                    buildFallbackWeb(data),
                    buildFallbackSms(data)
            );
        }
    }

    private String buildPrompt(LookupResponse data) {
        // Get top 3 resources for context
        List<FoodResource> top = data.getResources().stream()
                .limit(3)
                .toList();

        String resourceList = top.stream()
                .map(r -> String.format("- %s (%s, %.1f miles, %s, %s)",
                        r.getName(),
                        r.getType(),
                        r.getDistanceMiles(),
                        r.isOpenNow() ? "OPEN NOW" : "currently closed",
                        r.getHours()))
                .collect(Collectors.joining("\n"));

        return String.format("""
            You are HarvestPath, a warm and helpful community food access assistant.
            Someone just looked up food resources in their area. Generate two responses.

            LOCATION DATA:
            - Zip code: %s
            - Neighborhood: %s
            - Food desert: %s (%s severity)
            - Nearest SNAP/grocery store: %.1f miles away

            NEARBY RESOURCES:
            %s

            ENVIRONMENTAL CONTEXT:
            - Air quality: AQI %d (%s) — %s to walk outside
            - Water quality: %s (%d violations on record)

            INSTRUCTIONS:
            Write a warm, honest 3-4 sentence paragraph for the WEB view.
            - Tell them their food access situation clearly but without shame
            - Highlight the single most useful resource open right now or opening soonest
            - Mention AQI walking advice only if relevant (unhealthy air)
            - Mention water quality only if there are violations
            - Sound like a helpful neighbor, not a government form

            Then write [SMS] on a new line.

            Then write a 3-line SMS version (plain text, no markdown, under 300 chars total):
            - Line 1: nearest resource + distance + hours
            - Line 2: second nearest resource + hours
            - Line 3: one action they can take

            Be warm, non-judgmental, and focused on what people CAN access.
            """,
                data.getZipCode(),
                data.getNeighborhood(),
                data.isFoodDesert() ? "YES" : "NO",
                data.getFoodDesertSeverity(),
                data.getNearestGroceryMiles(),
                resourceList,
                data.getEnvironment().getAqiValue(),
                data.getEnvironment().getAqiCategory(),
                data.getEnvironment().isSafeToWalkOutdoors() ? "safe" : "not ideal",
                data.getEnvironment().getWqiStatus(),
                data.getEnvironment().getWaterViolationCount()
        );
    }

    // Used if Claude API fails
    private String buildFallbackWeb(LookupResponse data) {
        if (data.getResources().isEmpty()) {
            return "We couldn't find nearby food resources. Try calling 211 for local assistance.";
        }
        FoodResource nearest = data.getResources().get(0);
        return String.format(
                "Your nearest food resource is %s, about %.1f miles away (%s). %s",
                nearest.getName(),
                nearest.getDistanceMiles(),
                nearest.getHours(),
                data.getEnvironment().isSafeToWalkOutdoors()
                        ? "Air quality is good for walking today."
                        : "Consider transit options today due to air quality."
        );
    }

    // Used if Claude API fails or SMS split not found
    private String buildFallbackSms(LookupResponse data) {
        if (data.getResources().isEmpty()) {
            return "Call 211 for food assistance in your area.";
        }
        FoodResource r = data.getResources().get(0);
        return String.format("%s — %.1fmi — %s\nText SNAP %s for benefits help",
                r.getName(), r.getDistanceMiles(), r.getHours(), data.getZipCode());
    }

    public record ClaudeResponse(String webSummary, String smsSummary) {}
}