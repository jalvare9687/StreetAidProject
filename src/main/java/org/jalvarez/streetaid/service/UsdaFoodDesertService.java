package org.jalvarez.streetaid.service;

import com.opencsv.CSVReader;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

@Service
public class UsdaFoodDesertService {

    @Value("${usda.data.path}")
    private Resource usdaDataFile;

    // Stores census tract ID → food desert info
    private final Map<String, FoodDesertInfo> tractMap = new HashMap<>();

    @PostConstruct
    public void loadData() {
        try (CSVReader reader = new CSVReader(
                new InputStreamReader(usdaDataFile.getInputStream()))) {

            String[] headers = reader.readNext();

            int tractIdx = findIndex(headers, "CensusTract");
            int lilaIdx  = findIndex(headers, "LILATracts_1And10");
            int countyIdx = findIndex(headers, "County");
            int stateIdx  = findIndex(headers, "State");

            String[] row;
            while ((row = reader.readNext()) != null) {
                if (row.length <= lilaIdx) continue;

                String tractId   = row[tractIdx].trim();
                boolean isDesert = "1".equals(row[lilaIdx].trim());
                String county    = row[countyIdx].trim();
                String state     = row[stateIdx].trim();

                tractMap.put(tractId, new FoodDesertInfo(isDesert, county, state));
            }

            System.out.println("USDA data loaded: " + tractMap.size() + " tracts");

        } catch (Exception e) {
            System.err.println("Could not load USDA CSV: " + e.getMessage());
            loadAtlantaFallback();
        }
    }

    public FoodDesertInfo getFoodDesertInfo(String zipCode) {
        // Census tract IDs start with the county FIPS code
        // Fulton County GA = 13121, DeKalb = 13089
        // We scan tracts that start with the matching county FIPS
        String fips = zipToFips(zipCode);

        return tractMap.entrySet().stream()
                .filter(e -> e.getKey().startsWith(fips))
                .map(Map.Entry::getValue)
                .filter(FoodDesertInfo::isFoodDesert)
                .findFirst()
                .orElse(tractMap.entrySet().stream()
                        .filter(e -> e.getKey().startsWith(fips))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(new FoodDesertInfo(false, "Unknown", "GA")));
    }

    // Maps Atlanta zip codes to county FIPS codes
    private String zipToFips(String zip) {
        return switch (zip) {
            case "30301","30302","30303","30304","30305",
                 "30306","30307","30308","30309","30310",
                 "30311","30312","30313","30314","30315",
                 "30316","30317","30318","30319","30331",
                 "30336","30349","30354" -> "13121"; // Fulton County
            case "30032","30033","30034","30035","30058",
                 "30079","30083","30084","30088" -> "13089"; // DeKalb County
            case "30060","30061","30062","30063","30064",
                 "30065","30066","30067","30068","30069" -> "13067"; // Cobb County
            default -> "13121"; // Default to Fulton
        };
    }

    private int findIndex(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) return i;
        }
        throw new RuntimeException("Column not found: " + name);
    }

    private void loadAtlantaFallback() {
        // Real food desert zip codes in Atlanta — used if CSV fails to load
        tractMap.put("30314", new FoodDesertInfo(true, "Fulton County", "Georgia"));
        tractMap.put("30310", new FoodDesertInfo(true, "Fulton County", "Georgia"));
        tractMap.put("30318", new FoodDesertInfo(true, "Fulton County", "Georgia"));
        tractMap.put("30315", new FoodDesertInfo(true, "Fulton County", "Georgia"));
        tractMap.put("30306", new FoodDesertInfo(false, "Fulton County", "Georgia"));
        tractMap.put("30316", new FoodDesertInfo(false, "DeKalb County", "Georgia"));
        System.out.println("Loaded Atlanta fallback food desert data");
    }

    public record FoodDesertInfo(boolean isFoodDesert, String county, String state) {}
}
