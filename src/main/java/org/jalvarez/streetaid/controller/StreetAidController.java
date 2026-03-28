package org.jalvarez.streetaid.controller;

import org.jalvarez.streetaid.model.LookupResponse;
import org.jalvarez.streetaid.service.LivePost;
import org.jalvarez.streetaid.service.LivePostService;
import org.jalvarez.streetaid.service.LookupOrchestrationService;
import org.jalvarez.streetaid.service.TwilioSmsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class StreetAidController {

    private final LookupOrchestrationService orchestrationService;
    private final LivePostService livePostService;
    private final TwilioSmsService twilioSmsService;

    public StreetAidController(LookupOrchestrationService orchestrationService, LivePostService livePostService, TwilioSmsService twilioSmsService) {
        this.orchestrationService = orchestrationService;
        this.livePostService = livePostService;
        this.twilioSmsService = twilioSmsService;
    }

    // ── MAIN LOOKUP ───────────────────────────────────────────
    // GET /api/lookup?zip=30314
    @GetMapping("/lookup")
    public ResponseEntity<LookupResponse> lookup(@RequestParam String zip) {
        if (zip == null || !zip.matches("\\d{5}")) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(orchestrationService.lookup(zip));
    }

    // ── LIVE FEED ─────────────────────────────────────────────
    // GET /api/live          → all active posts
    // GET /api/live?zip=...  → posts for a specific zip
    @GetMapping("/live")
    public ResponseEntity<List<LivePost>> getLivePosts(
            @RequestParam(required = false) String zip
    ) {
        List<LivePost> posts = (zip != null && !zip.isBlank())
                ? livePostService.getActivePostsByZip(zip)
                : livePostService.getActivePosts();
        return ResponseEntity.ok(posts);
    }

    // POST /api/live — create post from web form
    @PostMapping("/live")
    public ResponseEntity<LivePost> createLivePost(
            @RequestBody Map<String, String> body
    ) {
        String orgName     = body.getOrDefault("orgName", "Anonymous");
        String description = body.get("description");
        String zipCode     = body.get("zipCode");
        String address     = body.getOrDefault("address", "");
        int expiryHours    = Integer.parseInt(
                body.getOrDefault("expiryHours", "4"));

        if (description == null || zipCode == null
                || !zipCode.matches("\\d{5}")) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(livePostService.createWebPost(
                orgName, description, zipCode, address, expiryHours));
    }

    // DELETE /api/live/{id} — close a post early
    @DeleteMapping("/live/{id}")
    public ResponseEntity<Void> closeLivePost(@PathVariable Long id) {
        livePostService.closeById(id);
        return ResponseEntity.ok().build();
    }

    // ── TWILIO SMS WEBHOOK ────────────────────────────────────
    // POST /api/sms/webhook
    @PostMapping("/sms/webhook")
    public ResponseEntity<String> handleSms(
            @RequestParam("From") String fromNumber,
            @RequestParam("Body") String messageBody
    ) {
        String reply = twilioSmsService.handleIncomingSms(
                fromNumber, messageBody.trim());

        // Twilio expects TwiML XML back
        String twiml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Response><Message>" + reply + "</Message></Response>";

        return ResponseEntity.ok()
                .header("Content-Type", "text/xml")
                .body(twiml);
    }

    // ── HEALTH CHECK ──────────────────────────────────────────
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("HarvestPath is running");
    }
}
