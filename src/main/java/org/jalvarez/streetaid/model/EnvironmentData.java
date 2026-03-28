package org.jalvarez.streetaid.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentData {
    // AQI
    private int aqiValue;
    private String aqiCategory;       // Good, Moderate, Unhealthy
    private String aqiPollutant;      // PM2.5, Ozone
    private boolean safeToWalkOutdoors;

    // Water
    private String wqiStatus;         // SAFE, MODERATE, VIOLATION
    private int waterViolationCount;
    private String waterViolationDetail;
    private String countyName;
}
