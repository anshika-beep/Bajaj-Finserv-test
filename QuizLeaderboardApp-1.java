package com.quiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

public class QuizLeaderboardApp {

    private static final String BASE_URL = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
    private static final String REG_NO = "RA2311003030187";
    private static final int TOTAL_POLLS = 10;
    private static final int DELAY_MS = 5000; // 5 seconds between polls

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    // Deduplication: key = "roundId|participant"
    private final Set<String> seenKeys = new HashSet<>();

    // Aggregated scores per participant
    private final Map<String, Integer> scores = new HashMap<>();

    public static void main(String[] args) throws Exception {
        new QuizLeaderboardApp().run();
    }

    public void run() throws Exception {
        System.out.println("=== Quiz Leaderboard System ===");
        System.out.println("Registration No: " + REG_NO);
        System.out.println();

        // Step 1 & 2: Poll API 10 times and collect responses
        for (int poll = 0; poll < TOTAL_POLLS; poll++) {
            System.out.printf("Polling %d/%d (poll index: %d)...%n", poll + 1, TOTAL_POLLS, poll);
            pollAndProcess(poll);

            if (poll < TOTAL_POLLS - 1) {
                System.out.printf("Waiting %d seconds before next poll...%n", DELAY_MS / 1000);
                Thread.sleep(DELAY_MS);
            }
        }

        System.out.println("\n=== All polls complete ===");

        // Step 5: Generate leaderboard sorted by totalScore descending
        List<Map<String, Object>> leaderboard = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("participant", e.getKey());
                    entry.put("totalScore", e.getValue());
                    return entry;
                })
                .collect(Collectors.toList());

        // Step 6: Compute total score
        int totalScore = scores.values().stream().mapToInt(Integer::intValue).sum();

        System.out.println("\n=== Leaderboard ===");
        leaderboard.forEach(e ->
            System.out.printf("  %-20s : %d%n", e.get("participant"), e.get("totalScore"))
        );
        System.out.println("Total combined score: " + totalScore);

        // Step 7: Submit leaderboard once
        submitLeaderboard(leaderboard);
    }

    private void pollAndProcess(int pollIndex) throws Exception {
        String url = String.format("%s/quiz/messages?regNo=%s&poll=%d", BASE_URL, REG_NO, pollIndex);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.err.printf("  [WARN] Poll %d returned HTTP %d: %s%n",
                    pollIndex, response.statusCode(), response.body());
            return;
        }

        // Parse response
        Map<?, ?> body = mapper.readValue(response.body(), Map.class);
        List<?> events = (List<?>) body.get("events");

        if (events == null) {
            System.out.println("  No events in this response.");
            return;
        }

        int newEvents = 0, dupEvents = 0;

        for (Object eventObj : events) {
            Map<?, ?> event = (Map<?, ?>) eventObj;
            String roundId = (String) event.get("roundId");
            String participant = (String) event.get("participant");
            int score = ((Number) event.get("score")).intValue();

            // Step 3: Deduplicate using roundId + participant
            String key = roundId + "|" + participant;

            if (seenKeys.add(key)) {
                // Step 4: Aggregate score (new unique entry)
                scores.merge(participant, score, Integer::sum);
                newEvents++;
            } else {
                dupEvents++;
            }
        }

        System.out.printf("  Poll %d: %d new events, %d duplicates skipped.%n",
                pollIndex, newEvents, dupEvents);
    }

    private void submitLeaderboard(List<Map<String, Object>> leaderboard) throws Exception {
        System.out.println("\n=== Submitting leaderboard... ===");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("regNo", REG_NO);
        payload.put("leaderboard", leaderboard);

        String jsonBody = mapper.writeValueAsString(payload);
        System.out.println("Payload: " + jsonBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/quiz/submit"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("HTTP Status: " + response.statusCode());
        System.out.println("Response: " + response.body());

        // Parse and display result clearly
        try {
            Map<?, ?> result = mapper.readValue(response.body(), Map.class);
            boolean isCorrect = Boolean.TRUE.equals(result.get("isCorrect"));
            System.out.println("\n" + (isCorrect ? "✅ SUCCESS: " : "❌ FAILED: ") + result.get("message"));
            System.out.println("Submitted Total : " + result.get("submittedTotal"));
            System.out.println("Expected Total  : " + result.get("expectedTotal"));
            System.out.println("Is Idempotent   : " + result.get("isIdempotent"));
        } catch (Exception e) {
            System.err.println("Could not parse submission response.");
        }
    }
}
