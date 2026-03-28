package org.jalvarez.streetaid.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class AirQualityService {

    private static final String AIRNOW_URL =
            "https://www.airnowapi.org/aq/observation/zipCode/current/";

    @Value("${airnow.api.key}")
    private String apiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public AirQualityService() {
        this.webClient = WebClient.create();
        this.objectMapper = new ObjectMapper();
    }

    public AqiResult getAqiForZip(String zipCode) {
        try {
            String url = AIRNOW_URL
                    + "?format=application/json"
                    + "&zipCode=" + zipCode
                    + "&distance=25"
                    + "&API_KEY=" + apiKey;

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);

            if (!root.isArray() || root.size() == 0) {
                System.out.println("AirNow returned no data for " + zipCode);
                return fallback();
            }

            // Loop through all readings and find the highest AQI
            int maxAqi = 0;
            String category = "Good";
            String pollutant = "PM2.5";

            for (JsonNode obs : root) {
                int aqi = obs.path("AQI").asInt(0);
                if (aqi > maxAqi) {
                    maxAqi   = aqi;
                    category = obs.path("Category").path("Name").asText("Good");
                    pollutant = obs.path("ParameterName").asText("PM2.5");
                }
            }

            return new AqiResult(maxAqi, category, pollutant);

        } catch (Exception e) {
            System.err.println("AirNow API error: " + e.getMessage());
            return fallback();
        }
    }

    public boolean isSafeToWalk(int aqiValue) {
        // Good (0-50) and Moderate (51-100) are safe for most people
        return aqiValue <= 100;
    }

    public String getWalkingAdvice(int aqiValue) {
        if (aqiValue <= 50)  return "Air quality is good — safe to walk.";
        if (aqiValue <= 100) return "Moderate air quality — most people can walk safely.";
        if (aqiValue <= 150) return "Unhealthy for sensitive groups — consider transit.";
        return "Poor air quality — avoid walking if possible.";
    }

    private AqiResult fallback() {
        // Atlanta average AQI when data is unavailable
        return new AqiResult(52, "Moderate", "PM2.5");
    }

    public record AqiResult(int value, String category, String pollutant) {}
}
