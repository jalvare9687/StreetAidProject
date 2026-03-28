package org.jalvarez.streetaid.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodResource {
    private String name;
    private String type;        // PANTRY, HOT_MEAL, SNAP, GARDEN, COOLING, WARMING
    private String address;
    private double latitude;
    private double longitude;
    private double distanceMiles;
    private String hours;
    private boolean openNow;
    private String phone;
    private String notes;
}