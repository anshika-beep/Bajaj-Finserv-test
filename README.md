# Bajaj-Finserv-test
# Quiz Leaderboard System

A Java application that polls a quiz API, deduplicates events, aggregates scores, and submits a leaderboard — built for the Bajaj Finserv Health / SRM Internship Assignment.

---

## How It Works

1. **Polls** the `/quiz/messages` API 10 times (poll index 0–9) with a 5-second delay between each call
2. **Deduplicates** events using the composite key `roundId + participant` — if the same entry appears in multiple polls, it is counted only once
3. **Aggregates** scores per participant across all unique events
4. **Builds a leaderboard** sorted by `totalScore` (descending)
5. **Submits** the leaderboard once to `/quiz/submit`

---

## Duplicate Handling

The validator may return the same event data across multiple polls. To handle this:

```
Seen key = roundId + "|" + participant
```

If this key was already processed, the event is silently skipped. This ensures each round score per participant is counted exactly once, regardless of how many times it appears across polls.

---

## Prerequisites

- Java 11 or higher
- Maven 3.6+

---

## Setup

### 1. Clone the repo

```bash
git clone https://github.com/your-username/quiz-leaderboard.git
cd quiz-leaderboard
```

### 2. Set your Registration Number

Open `src/main/java/com/quiz/QuizLeaderboardApp.java` and replace:

```java
private static final String REG_NO = "YOUR_REG_NO";
```

with your actual registration number, e.g.:

```java
private static final String REG_NO = "2024CS101";
```

### 3. Build

```bash
mvn clean package
```

This produces `target/quiz-leaderboard.jar`.

### 4. Run

```bash
java -jar target/quiz-leaderboard.jar
```

---

## Sample Output

```
=== Quiz Leaderboard System ===
Registration No: 2024CS101

Polling 1/10 (poll index: 0)...
  Poll 0: 5 new events, 0 duplicates skipped.
Waiting 5 seconds before next poll...
Polling 2/10 (poll index: 1)...
  Poll 1: 3 new events, 2 duplicates skipped.
...

=== Leaderboard ===
  Alice                : 120
  Bob                  : 100
  Charlie              : 80
Total combined score: 300

=== Submitting leaderboard... ===
HTTP Status: 200
✅ SUCCESS: Correct!
Submitted Total : 300
Expected Total  : 300
Is Idempotent   : true
```

---

## Project Structure

```
quiz-leaderboard/
├── pom.xml
├── README.md
└── src/
    └── main/
        └── java/
            └── com/
                └── quiz/
                    └── QuizLeaderboardApp.java
```

---

## Key Design Decisions

| Concern | Approach |
|---|---|
| Deduplication | `HashSet` of `"roundId\|participant"` keys |
| Score aggregation | `HashMap<String, Integer>` with `merge()` |
| HTTP client | Java 11 built-in `HttpClient` (no extra deps) |
| JSON parsing | Jackson `ObjectMapper` |
| Sorting | Stream sorted by value descending |
| Single submission | Submit called exactly once after all polls |
