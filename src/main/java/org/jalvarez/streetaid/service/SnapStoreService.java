package org.jalvarez.streetaid.service;

import com.opencsv.CSVReader;
import jakarta.annotation.PostConstruct;
import org.jalvarez.streetaid.model.FoodResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class SnapStoreService {

    @Value("${snap.data.path}")
    private Resource snapDataFile;

    private final List<SnapStore> stores = new ArrayList<>();

    @PostConstruct
    public void loadData() {
        try (CSVReader reader = new CSVReader(
                new InputStreamReader(snapDataFile.getInputStream()))) {

            String[] headers = reader.readNext();

            int nameIdx  = findIndex(headers, "Store Name");
            int latIdx   = findIndex(headers, "Latitude");
            int lngIdx   = findIndex(headers, "Longitude");
            int addrIdx  = findIndex(headers, "Store Street Address");
            int cityIdx  = findIndex(headers, "City");
            int zipIdx   = findIndex(headers, "Zip Code");

            String[] row;
            while ((row = reader.readNext()) != null) {
                if (row.length <= Math.max(latIdx, lngIdx)) continue;
                try {
                    String name    = row[nameIdx].trim();
                    double lat     = Double.parseDouble(row[latIdx].trim());
                    double lng     = Double.parseDouble(row[lngIdx].trim());
                    String address = row[addrIdx].trim() + ", " + row[cityIdx].trim();
                    String zip     = row[zipIdx].trim();
                    stores.add(new SnapStore(name, lat, lng, address, zip));
                } catch (NumberFormatException ignored) {}
            }

            System.out.println("SNAP stores loaded: " + stores.size());

        } catch (Exception e) {
            System.err.println("Could not load SNAP CSV: " + e.getMessage());
            loadFallback();
        }
    }

    // Returns nearest SNAP stores as FoodResource objects for the map
    public List<FoodResource> getNearestSnapStores(double lat, double lng, int limit) {
        return stores.stream()
                .map(s -> {
                    double dist = haversineDistance(lat, lng, s.lat, s.lng);
                    return FoodResource.builder()
                            .name(s.name)
                            .type("SNAP")
                            .address(s.address)
                            .latitude(s.lat)
                            .longitude(s.lng)
                            .distanceMiles(Math.round(dist * 10.0) / 10.0)
                            .hours("Check store hours")
                            .openNow(false)
                            .notes("Accepts SNAP / EBT")
                            .build();
                })
                .filter(r -> r.getDistanceMiles() < 5.0)
                .sorted(Comparator.comparingDouble(FoodResource::getDistanceMiles))
                .limit(limit)
                .toList();
    }

    // Returns just the distance to the nearest SNAP store
    // Used to determine food desert severity
    public double getNearestSnapDistance(double lat, double lng) {
        return stores.stream()
                .mapToDouble(s -> haversineDistance(lat, lng, s.lat, s.lng))
                .min()
                .orElse(5.0);
    }

    private double haversineDistance(double lat1, double lon1,
                                     double lat2, double lon2) {
        final int R = 3959; // Earth radius in miles
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private int findIndex(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().replaceAll("^\uFEFF", "")
                    .equalsIgnoreCase(name)) return i;
        }
        throw new RuntimeException("Column not found in SNAP CSV: " + name);
    }

    private void loadFallback() {
        stores.add(new SnapStore("ALDI", 33.7280, -84.4160,
                "883 Ralph D Abernathy Blvd, Atlanta", "30310"));
        stores.add(new SnapStore("Kroger West End", 33.7350, -84.4080,
                "99 Langhorn St SW, Atlanta", "30310"));
        stores.add(new SnapStore("Save-A-Lot", 33.7610, -84.4220,
                "English Ave, Atlanta", "30318"));
        System.out.println("Loaded SNAP fallback data");
    }

    private record SnapStore(String name, double lat, double lng,
                             String address, String zip) {}
}

