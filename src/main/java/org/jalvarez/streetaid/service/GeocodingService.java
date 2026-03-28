package org.jalvarez.streetaid.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class GeocodingService {

    private static final String GEOCODE_URL =
            "https://maps.googleapis.com/maps/api/geocode/json";

    @Value("${google.maps.api.key}")
    private String apiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // Hardcoded Atlanta zip codes so we don't burn API calls during demo
    private static final Map<String, double[]> ZIP_COORDS = Map.of(
            "30314", new double[]{33.7560, -84.4330},
            "30310", new double[]{33.7277, -84.4150},
            "30318", new double[]{33.7920, -84.4300},
            "30315", new double[]{33.7060, -84.3790},
            "30316", new double[]{33.7231, -84.3430},
            "30303", new double[]{33.7490, -84.3880},
            "30312", new double[]{33.7440, -84.3760},
            "30306", new double[]{33.7800, -84.3530}
    );

    private static final Map<String, String> ZIP_NAMES = Map.of(
            "30314", "Vine City / English Avenue",
            "30310", "West End",
            "30318", "Bankhead / Grove Park",
            "30315", "Summerhill / Mechanicsville",
            "30316", "East Atlanta Village",
            "30303", "Downtown Atlanta",
            "30312", "Old Fourth Ward",
            "30306", "Virginia Highland"
    );

    public GeocodingService() {
        this.webClient = WebClient.create();
        this.objectMapper = new ObjectMapper();
    }

    public GeoResult geocodeZip(String zipCode) {
        // Use hardcoded coords first — instant, no API call needed
        if (ZIP_COORDS.containsKey(zipCode)) {
            double[] coords = ZIP_COORDS.get(zipCode);
            String name = ZIP_NAMES.getOrDefault(zipCode, "Atlanta, GA " + zipCode);
            return new GeoResult(coords[0], coords[1], name);
        }

        // Fall back to Google Geocoding API for unknown zips
        try {
            String url = GEOCODE_URL
                    + "?address=" + zipCode + ",GA"
                    + "&key=" + apiKey;

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.get("results");

            if (results != null && results.size() > 0) {
                JsonNode location = results.get(0)
                        .get("geometry")
                        .get("location");
                double lat = location.get("lat").asDouble();
                double lng = location.get("lng").asDouble();
                String name = results.get(0)
                        .get("formatted_address").asText();
                return new GeoResult(lat, lng, name);
            }

        } catch (Exception e) {
            System.err.println("Geocoding error: " + e.getMessage());
        }

        // Last resort — Atlanta city center
        return new GeoResult(33.7490, -84.3880, "Atlanta, GA " + zipCode);
    }

    public record GeoResult(double lat, double lng, String name) {}
}