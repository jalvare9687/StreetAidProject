package org.jalvarez.streetaid.service;

import org.jalvarez.streetaid.model.LookupResponse;
import org.springframework.stereotype.Service;

@Service
public class TwilioSmsService {

    private final LookupOrchestrationService orchestrationService;
    private final LivePostService livePostService;
    private final WaterQualityService waterQualityService;

    public TwilioSmsService(
            LookupOrchestrationService orchestrationService,
            LivePostService livePostService,
            WaterQualityService waterQualityService
    ) {
        this.orchestrationService = orchestrationService;
        this.livePostService      = livePostService;
        this.waterQualityService  = waterQualityService;
    }

    public String handleIncomingSms(String fromPhone, String body) {
        String upper = body.toUpperCase().trim();

        if (upper.equals("HELP") || upper.equals("HI") || upper.equals("HELLO")) {
            return "HarvestPath Commands:\n"
                    + "FOOD [zip] - find food near you\n"
                    + "POST [zip] [what] until [time] - share surplus food\n"
                    + "RESTOCK [name] [zip] - mark resource restocked\n"
                    + "EMPTY [name] [zip] - flag resource empty\n"
                    + "CLOSE - remove your post\n"
                    + "SNAP [zip] - benefits info\n"
                    + "WATER [zip] - water quality";
        }

        if (upper.equals("CLOSE")) {
            int count = livePostService.closeByPhone(fromPhone);
            return count > 0
                    ? "Removed " + count + " post(s). Thank you!"
                    : "No active posts found for your number.";
        }

        String[] parts = body.trim().split("\\s+", 4);
        if (parts.length < 2) {
            return "Text HELP for available commands.\nExample: FOOD 30314";
        }

        String command = parts[0].toUpperCase();

        return switch (command) {
            case "FOOD"    -> handleFood(parts);
            case "POST"    -> handlePost(fromPhone, parts, body);
            case "RESTOCK" -> handleRestock(fromPhone, parts);
            case "EMPTY"   -> handleEmpty(fromPhone, parts);
            case "SNAP"    -> handleSnap(parts);
            case "WATER"   -> handleWater(parts);
            default -> "Unknown command. Text HELP for options.";
        };
    }

    private String handleFood(String[] parts) {
        String zip = extractZip(parts, 1);
        if (zip == null) return "Include your zip.\nExample: FOOD 30314";
        try {
            LookupResponse data = orchestrationService.lookup(zip);
            return data.getSmsSummary();
        } catch (Exception e) {
            return "Could not find resources for " + zip + ". Try calling 211.";
        }
    }

    // POST 30314 Bagels and bread free until 9pm
    private String handlePost(String fromPhone, String[] parts, String fullBody) {
        String zip = extractZip(parts, 1);
        if (zip == null) return "Format: POST [zip] [what] until [time]\nExample: POST 30314 Bagels free until 9pm";

        String rest = fullBody.substring(
                fullBody.indexOf(zip) + zip.length()).trim();
        String description = rest;
        String until = "";

        if (rest.toLowerCase().contains("until")) {
            int idx = rest.toLowerCase().indexOf("until");
            description = rest.substring(0, idx).trim();
            until = rest.substring(idx + 5).trim();
        }

        if (description.isBlank()) {
            return "Include a description.\nExample: POST 30314 Bagels free until 9pm";
        }

        LivePost post = livePostService.createSmsPost(
                fromPhone, zip, description, until);
        String expiry = livePostService.formatExpiryForSms(post.getExpiresAt());
        return "Listed! Expires at " + expiry + ".\nText CLOSE to remove early.\nThank you!";
    }

    // RESTOCK West End Pantry 30314
    private String handleRestock(String fromPhone, String[] parts) {
        if (parts.length < 3) {
            return "Format: RESTOCK [name] [zip]\nExample: RESTOCK West End Pantry 30314";
        }
        String zip = parts[parts.length - 1].replaceAll("[^0-9]", "");
        if (zip.length() != 5) {
            return "Include zip at the end.\nExample: RESTOCK West End Pantry 30314";
        }
        String name = String.join(" ",
                java.util.Arrays.copyOfRange(parts, 1, parts.length - 1));
        livePostService.createRestockAlert(name, zip, fromPhone);
        return name + " marked as restocked. Alert expires in 2 hours. Thank you!";
    }

    // EMPTY community fridge 30314
    private String handleEmpty(String fromPhone, String[] parts) {
        if (parts.length < 3) {
            return "Format: EMPTY [name] [zip]\nExample: EMPTY community fridge 30314";
        }
        String zip = parts[parts.length - 1].replaceAll("[^0-9]", "");
        if (zip.length() != 5) {
            return "Include zip at the end.\nExample: EMPTY community fridge 30314";
        }
        String name = String.join(" ",
                java.util.Arrays.copyOfRange(parts, 1, parts.length - 1));
        livePostService.createEmptyFlag(name, zip, fromPhone);
        return "Flagged as empty. Alert expires in 30 min. Thank you!";
    }

    private String handleSnap(String[] parts) {
        String zip = extractZip(parts, 1);
        return "SNAP Benefits" + (zip != null ? " for " + zip : "") + ":\n"
                + "Apply: compass.ga.gov\n"
                + "Call: 1-877-423-4746\n"
                + "Limit: ~$1,580/mo single";
    }

    private String handleWater(String[] parts) {
        String zip = extractZip(parts, 1);
        if (zip == null) return "Include your zip.\nExample: WATER 30314";
        WaterQualityService.WqiResult result =
                waterQualityService.getWaterQualityForZip(zip);
        return "Water quality for " + zip + ": "
                + result.status() + "\n" + result.detail();
    }

    private String extractZip(String[] parts, int idx) {
        if (parts.length <= idx) return null;
        String zip = parts[idx].replaceAll("[^0-9]", "");
        return zip.length() == 5 ? zip : null;
    }
}
