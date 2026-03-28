package org.jalvarez.streetaid.service;


import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class MartaService {

    @Value("${marta.stops.path}")
    private Resource stopsFile;

    private final List<MartaStop> stops = new ArrayList<>();

    @PostConstruct
    public void loadStops() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stopsFile.getInputStream()))) {

            String header = reader.readLine();
            if (header == null) return;

            String[] cols = header.split(",");
            int nameIdx = findIndex(cols, "stop_name");
            int latIdx  = findIndex(cols, "stop_lat");
            int lngIdx  = findIndex(cols, "stop_lon");

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length <= Math.max(latIdx, lngIdx)) continue;
                try {
                    String name = parts[nameIdx].replace("\"", "").trim();
                    double lat  = Double.parseDouble(parts[latIdx].replace("\"", "").trim());
                    double lng  = Double.parseDouble(parts[lngIdx].replace("\"", "").trim());
                    stops.add(new MartaStop(name, lat, lng));
                } catch (NumberFormatException ignored) {}
            }

            System.out.println("MARTA stops loaded: " + stops.size());

        } catch (Exception e) {
            System.err.println("MARTA stops file not found — using fallback.");
            loadFallback();
        }
    }

    public List<NearbyStop> getNearestStops(double lat, double lng, int limit) {
        return stops.stream()
                .map(s -> new NearbyStop(s.name(), s.lat(), s.lng(),
                        haversineDistance(lat, lng, s.lat(), s.lng())))
                .filter(s -> s.distanceMiles() < 0.5)
                .sorted(Comparator.comparingDouble(NearbyStop::distanceMiles))
                .limit(limit)
                .toList();
    }

    public boolean isTransitAccessible(double lat, double lng) {
        return stops.stream()
                .anyMatch(s -> haversineDistance(lat, lng, s.lat(), s.lng()) <= 0.5);
    }

    private int findIndex(String[] cols, String name) {
        for (int i = 0; i < cols.length; i++) {
            if (cols[i].replace("\"", "").trim().equalsIgnoreCase(name)) return i;
        }
        return 0;
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

    private void loadFallback() {
        stops.add(new MartaStop("West End Station", 33.7358, -84.4120));
        stops.add(new MartaStop("Vine City Station", 33.7560, -84.4140));
        stops.add(new MartaStop("Ashby Station",     33.7560, -84.4310));
        stops.add(new MartaStop("Garnett Station",   33.7490, -84.3990));
        stops.add(new MartaStop("Five Points Station", 33.7539, -84.3915));
    }

    private record MartaStop(String name, double lat, double lng) {}
    public record NearbyStop(String name, double lat, double lng, double distanceMiles) {}
}
