package org.jalvarez.streetaid.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jalvarez.streetaid.model.FoodResource;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class OverpassFoodService {

    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";
    private static final int RADIUS_METERS = 5000; // ~3 miles

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OverpassFoodService() {
        this.webClient = WebClient.builder()
                .baseUrl(OVERPASS_URL)
                .defaultHeader("User-Agent", "HarvestPath/1.0")
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public List<FoodResource> findFoodResources(double lat, double lng) {
        String query = buildQuery(lat, lng);

        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String response = webClient.get()
                    .uri("?data=" + encoded)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseResponse(response, lat, lng);

        } catch (Exception e) {
            System.err.println("Overpass API error: " + e.getMessage());
            return getFallbackResources(lat, lng);
        }
    }

    private String buildQuery(double lat, double lng) {
        return String.format("""
            [out:json][timeout:25];
            (
              node["social_facility"="food_bank"](around:%d,%f,%f);
              way["social_facility"="food_bank"](around:%d,%f,%f);
              node["social_facility"="soup_kitchen"](around:%d,%f,%f);
              way["social_facility"="soup_kitchen"](around:%d,%f,%f);
              node["social_facility"="shelter"](around:%d,%f,%f);
              way["social_facility"="shelter"](around:%d,%f,%f);
              node["amenity"="food_bank"](around:%d,%f,%f);
              node["amenity"="drinking_water"](around:%d,%f,%f);
              node["amenity"="food_sharing"](around:%d,%f,%f);
              node["leisure"="garden"]["garden:type"="community"](around:%d,%f,%f);
            );
            out center tags;
            """,
                RADIUS_METERS, lat, lng,
                RADIUS_METERS, lat, lng,
                RADIUS_METERS, lat, lng,
                RADIUS_METERS, lat, lng,
                RADIUS_METERS, lat, lng,
                RADIUS_METERS, lat, lng,
                RADIUS_METERS, lat, lng,
                RADIUS_METERS, lat, lng,
                RADIUS_METERS, lat, lng,
                RADIUS_METERS, lat, lng
        );
    }

    private List<FoodResource> parseResponse(String json, double userLat, double userLng) {
        List<FoodResource> resources = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode elements = root.get("elements");
            if (elements == null) return resources;

            for (JsonNode el : elements) {
                JsonNode tags = el.get("tags");
                if (tags == null) continue;

                // Handle both node (direct lat/lon) and way (center lat/lon)
                double elLat, elLng;
                if (el.has("lat")) {
                    elLat = el.get("lat").asDouble();
                    elLng = el.get("lon").asDouble();
                } else if (el.has("center")) {
                    elLat = el.get("center").get("lat").asDouble();
                    elLng = el.get("center").get("lon").asDouble();
                } else continue;

                double distance = haversineDistance(userLat, userLng, elLat, elLng);
                String name     = getTag(tags, "name", "Unnamed Resource");
                String type     = classifyType(tags);
                String hours    = getTag(tags, "opening_hours", "Call for hours");
                String phone    = getTag(tags, "phone", "");
                String address  = buildAddress(tags);

                resources.add(FoodResource.builder()
                        .name(name)
                        .type(type)
                        .address(address)
                        .latitude(elLat)
                        .longitude(elLng)
                        .distanceMiles(Math.round(distance * 10.0) / 10.0)
                        .hours(hours)
                        .openNow(estimateOpenNow(hours))
                        .phone(phone)
                        .build());
            }

            resources.sort(Comparator.comparingDouble(FoodResource::getDistanceMiles));

        } catch (Exception e) {
            System.err.println("Overpass parse error: " + e.getMessage());
        }
        return resources;
    }

    private String classifyType(JsonNode tags) {
        String social  = getTag(tags, "social_facility", "");
        String amenity = getTag(tags, "amenity", "");
        String leisure = getTag(tags, "leisure", "");

        if (social.equals("food_bank") || amenity.equals("food_bank")) return "PANTRY";
        if (social.equals("soup_kitchen"))  return "HOT_MEAL";
        if (social.equals("shelter"))       return "SHELTER";
        if (amenity.equals("drinking_water")) return "WATER";
        if (amenity.equals("food_sharing")) return "FRIDGE";
        if (leisure.equals("garden"))       return "GARDEN";
        return "RESOURCE";
    }

    private String buildAddress(JsonNode tags) {
        String number = getTag(tags, "addr:housenumber", "");
        String street = getTag(tags, "addr:street", "");
        String city   = getTag(tags, "addr:city", "Atlanta");
        String street_full = (number + " " + street).trim();
        return street_full.isEmpty() ? city : street_full + ", " + city;
    }

    private boolean estimateOpenNow(String hours) {
        if (hours == null || hours.equals("Call for hours")) return false;
        if (hours.equals("24/7")) return true;
        String today = DayOfWeek.from(java.time.LocalDate.now())
                .getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        return hours.contains(today) || hours.toLowerCase().contains("mo-fr");
    }

    private String getTag(JsonNode tags, String key, String defaultVal) {
        JsonNode node = tags.get(key);
        return node != null ? node.asText() : defaultVal;
    }

    private double haversineDistance(double lat1, double lon1,
                                     double lat2, double lon2) {
        final int R = 3959;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private List<FoodResource> getFallbackResources(double lat, double lng) {
        return List.of(
                FoodResource.builder()
                        .name("West End Food Pantry").type("PANTRY")
                        .address("883 Ralph D. Abernathy Blvd, Atlanta")
                        .latitude(33.7384).longitude(-84.4150)
                        .distanceMiles(haversineDistance(lat, lng, 33.7384, -84.4150))
                        .hours("Tue/Thu 10am-2pm").openNow(false).build(),
                FoodResource.builder()
                        .name("Hosea Feeds the Hungry").type("HOT_MEAL")
                        .address("One Hope St, Atlanta")
                        .latitude(33.7490).longitude(-84.4020)
                        .distanceMiles(haversineDistance(lat, lng, 33.7490, -84.4020))
                        .hours("Sat 9am-12pm").openNow(false).build(),
                FoodResource.builder()
                        .name("Vine City Community Garden").type("GARDEN")
                        .address("Vine City, Atlanta")
                        .latitude(33.7520).longitude(-84.4120)
                        .distanceMiles(haversineDistance(lat, lng, 33.7520, -84.4120))
                        .hours("Dawn to dusk").openNow(true).build()
        );
    }
}
