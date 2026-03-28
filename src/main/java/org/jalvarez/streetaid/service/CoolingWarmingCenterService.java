package org.jalvarez.streetaid.service;

import org.jalvarez.streetaid.model.FoodResource;
import org.springframework.stereotype.Service;

import java.time.Month;
import java.util.Comparator;
import java.util.List;

@Service
public class CoolingWarmingCenterService {

    private static final List<FoodResource> COOLING_CENTERS = List.of(
            FoodResource.builder()
                    .name("West End Branch Library").type("COOLING")
                    .address("525 Murphy Ave SW, Atlanta, GA 30310")
                    .latitude(33.7322).longitude(-84.4161)
                    .hours("Mon-Thu 10am-8pm, Fri-Sat 10am-5pm")
                    .openNow(true)
                    .notes("Free AC, internet, water. No ID required.")
                    .build(),
            FoodResource.builder()
                    .name("Vine City Recreation Center").type("COOLING")
                    .address("125 Northside Dr NW, Atlanta, GA 30314")
                    .latitude(33.7558).longitude(-84.4140)
                    .hours("Mon-Fri 7am-9pm, Sat 9am-6pm")
                    .openNow(true)
                    .notes("City-run cooling center. Water and seating available.")
                    .build(),
            FoodResource.builder()
                    .name("Adamsville Recreation Center").type("COOLING")
                    .address("3201 MLK Jr Dr SW, Atlanta, GA 30311")
                    .latitude(33.7329).longitude(-84.4810)
                    .hours("Mon-Fri 7am-9pm")
                    .openNow(true)
                    .notes("Free AC and water.")
                    .build(),
            FoodResource.builder()
                    .name("Central Library").type("COOLING")
                    .address("1 Margaret Mitchell Square, Atlanta, GA 30303")
                    .latitude(33.7567).longitude(-84.3888)
                    .hours("Mon-Thu 9am-8pm, Fri-Sat 9am-6pm, Sun 1pm-6pm")
                    .openNow(true)
                    .notes("Public library. No ID required.")
                    .build()
    );

    private static final List<FoodResource> WARMING_CENTERS = List.of(
            FoodResource.builder()
                    .name("Atlanta Mission").type("WARMING")
                    .address("250 Peachtree St NW, Atlanta, GA 30303")
                    .latitude(33.7578).longitude(-84.3871)
                    .hours("24hrs during cold weather alerts")
                    .openNow(true)
                    .notes("Emergency winter shelter. Call 404-588-4000.")
                    .build(),
            FoodResource.builder()
                    .name("Salvation Army Atlanta").type("WARMING")
                    .address("897 Ralph David Abernathy Blvd SW, Atlanta, GA 30310")
                    .latitude(33.7361).longitude(-84.4139)
                    .hours("Mon-Fri 8am-5pm, extended during cold alerts")
                    .openNow(true)
                    .notes("Warming center and hot meals during cold weather.")
                    .build(),
            FoodResource.builder()
                    .name("Crossroads Community Ministries").type("WARMING")
                    .address("96 Poplar St NW, Atlanta, GA 30303")
                    .latitude(33.7531).longitude(-84.3934)
                    .hours("Mon-Fri 7am-3pm")
                    .openNow(true)
                    .notes("Daytime warming center. Breakfast and lunch served.")
                    .build()
    );

    public List<FoodResource> getNearbyCenters(double lat, double lng) {
        List<FoodResource> centers = isWinter() ? WARMING_CENTERS : COOLING_CENTERS;

        return centers.stream()
                .map(c -> FoodResource.builder()
                        .name(c.getName())
                        .type(c.getType())
                        .address(c.getAddress())
                        .latitude(c.getLatitude())
                        .longitude(c.getLongitude())
                        .distanceMiles(Math.round(
                                haversineDistance(lat, lng, c.getLatitude(), c.getLongitude()) * 10.0) / 10.0)
                        .hours(c.getHours())
                        .openNow(c.isOpenNow())
                        .notes(c.getNotes())
                        .build())
                .sorted(Comparator.comparingDouble(FoodResource::getDistanceMiles))
                .limit(2)
                .toList();
    }

    public boolean isWinter() {
        Month m = java.time.LocalDate.now().getMonth();
        return m == Month.NOVEMBER || m == Month.DECEMBER
                || m == Month.JANUARY  || m == Month.FEBRUARY
                || m == Month.MARCH;
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
}

