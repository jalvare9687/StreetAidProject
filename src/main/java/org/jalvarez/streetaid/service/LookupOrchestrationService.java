package org.jalvarez.streetaid.service;

import org.jalvarez.streetaid.model.EnvironmentData;
import org.jalvarez.streetaid.model.FoodResource;
import org.jalvarez.streetaid.model.LookupResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class LookupOrchestrationService {

    private final GeocodingService geocodingService;
    private final UsdaFoodDesertService usdaService;
    private final OverpassFoodService overpassService;
    private final SnapStoreService snapStoreService;
    private final MartaService martaService;
    private final CoolingWarmingCenterService coolingWarmingService;
    private final AirQualityService airQualityService;
    private final WaterQualityService waterQualityService;
    private final ClaudeAiService claudeAiService;

    public LookupOrchestrationService(
            GeocodingService geocodingService,
            UsdaFoodDesertService usdaService,
            OverpassFoodService overpassService,
            SnapStoreService snapStoreService,
            MartaService martaService,
            CoolingWarmingCenterService coolingWarmingService,
            AirQualityService airQualityService,
            WaterQualityService waterQualityService,
            ClaudeAiService claudeAiService
    ) {
        this.geocodingService      = geocodingService;
        this.usdaService           = usdaService;
        this.overpassService       = overpassService;
        this.snapStoreService      = snapStoreService;
        this.martaService          = martaService;
        this.coolingWarmingService = coolingWarmingService;
        this.airQualityService     = airQualityService;
        this.waterQualityService   = waterQualityService;
        this.claudeAiService       = claudeAiService;
    }

    public LookupResponse lookup(String zipCode) {

        // 1. Zip → lat/lng
        GeocodingService.GeoResult geo =
                geocodingService.geocodeZip(zipCode);

        // 2. Is this a food desert?
        UsdaFoodDesertService.FoodDesertInfo desertInfo =
                usdaService.getFoodDesertInfo(zipCode);

        // 3. Nearby food resources from OpenStreetMap
        List<FoodResource> osmResources =
                overpassService.findFoodResources(geo.lat(), geo.lng());

        // 4. Nearby SNAP stores from CSV
        List<FoodResource> snapStores =
                snapStoreService.getNearestSnapStores(geo.lat(), geo.lng(), 3);

        // 5. Cooling or warming centers
        List<FoodResource> centers =
                coolingWarmingService.getNearbyCenters(geo.lat(), geo.lng());

        // 6. Merge all resources and sort by distance
        List<FoodResource> allResources = new ArrayList<>();
        allResources.addAll(osmResources);
        allResources.addAll(snapStores);
        allResources.addAll(centers);
        allResources.sort(Comparator.comparingDouble(FoodResource::getDistanceMiles));

        // 7. AQI
        AirQualityService.AqiResult aqi =
                airQualityService.getAqiForZip(zipCode);

        // 8. Water quality
        WaterQualityService.WqiResult wqi =
                waterQualityService.getWaterQualityForZip(zipCode);

        // 9. Food desert severity based on nearest SNAP store
        double nearestSnap =
                snapStoreService.getNearestSnapDistance(geo.lat(), geo.lng());

        String severity = desertInfo.isFoodDesert()
                ? (nearestSnap > 1.5 ? "SEVERE" : "MODERATE")
                : "LOW";

        // 10. Build environment model
        EnvironmentData environment = EnvironmentData.builder()
                .aqiValue(aqi.value())
                .aqiCategory(aqi.category())
                .aqiPollutant(aqi.pollutant())
                .safeToWalkOutdoors(airQualityService.isSafeToWalk(aqi.value()))
                .wqiStatus(wqi.status())
                .waterViolationCount(wqi.violationCount())
                .waterViolationDetail(wqi.detail())
                .countyName(desertInfo.county())
                .build();

        // 11. Assemble the full response
        LookupResponse response = LookupResponse.builder()
                .zipCode(zipCode)
                .neighborhood(geo.name())
                .latitude(geo.lat())
                .longitude(geo.lng())
                .isFoodDesert(desertInfo.isFoodDesert())
                .foodDesertSeverity(severity)
                .nearestGroceryMiles(nearestSnap)
                .resources(allResources)
                .environment(environment)
                .build();

        // 12. Generate Claude AI summary
        ClaudeAiService.ClaudeResponse ai =
                claudeAiService.generateSummary(response);
        response.setAiSummary(ai.webSummary());
        response.setSmsSummary(ai.smsSummary());

        return response;
    }
}
