package org.jalvarez.streetaid.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class WaterQualityService {

    private static final String ECHO_URL =
            "https://echo.epa.gov/rest-services/sdw_rest_services.getSystems";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public WaterQualityService() {
        this.webClient = WebClient.create();
        this.objectMapper = new ObjectMapper();
    }

    public WqiResult getWaterQualityForZip(String zipCode) {
        try {
            String url = ECHO_URL
                    + "?output=JSON"
                    + "&p_zip=" + zipCode
                    + "&p_pws_activity_status=A"
                    + "&responseset=5";

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseResponse(response);

        } catch (Exception e) {
            System.err.println("EPA ECHO error: " + e.getMessage());
            return fallback();
        }
    }

    private WqiResult parseResponse(String json) {
        try {
            JsonNode root    = objectMapper.readTree(json);
            JsonNode results = root.path("Results").path("Results");

            int totalViolations  = 0;
            int healthViolations = 0;

            if (results.isArray()) {
                for (JsonNode system : results) {
                    int vio = system.path("VioCount").asInt(0);
                    totalViolations += vio;
                    if (vio > 0) healthViolations += vio;
                }
            }

            if (healthViolations > 2) {
                return new WqiResult(totalViolations, "VIOLATION",
                        "Health-based violations detected. Use filtered water for drinking.");
            } else if (totalViolations > 0) {
                return new WqiResult(totalViolations, "MODERATE",
                        "Minor water quality concerns noted in your area.");
            } else {
                return new WqiResult(0, "SAFE",
                        "Water quality meets EPA standards.");
            }

        } catch (Exception e) {
            System.err.println("ECHO parse error: " + e.getMessage());
            return fallback();
        }
    }

    private WqiResult fallback() {
        return new WqiResult(0, "SAFE", "Water quality data unavailable for this area.");
    }

    public record WqiResult(int violationCount, String status, String detail) {}
}
