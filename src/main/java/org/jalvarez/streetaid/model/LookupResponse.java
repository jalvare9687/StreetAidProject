package org.jalvarez.streetaid.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LookupResponse {
    private String zipCode;
    private String neighborhood;
    private double latitude;
    private double longitude;

    // Food desert status
    private boolean isFoodDesert;
    private String foodDesertSeverity;    // SEVERE, MODERATE, LOW
    private double nearestGroceryMiles;

    // All nearby resources sorted by distance
    private List<FoodResource> resources;

    // Environmental context
    private EnvironmentData environment;

    // Claude AI generated text
    private String aiSummary;

    // Short version for SMS
    private String smsSummary;
}