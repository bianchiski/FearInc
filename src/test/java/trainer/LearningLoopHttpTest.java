package trainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class LearningLoopHttpTest {
  private HttpServer server;
  private Path databaseDirectory;
  private URI baseUri;
  private String cookie;
  private final HttpClient client = HttpClient.newHttpClient();

  @BeforeEach
  void setUp() throws Exception {
    databaseDirectory = Files.createTempDirectory("trainer-http-test-");
    System.setProperty("trainer.db", databaseDirectory.resolve("trainer.db").toString());
    server = Main.createServer(0);
    server.start();
    baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  @AfterEach
  void tearDown() throws Exception {
    if (server != null) server.stop(0);
    System.clearProperty("trainer.db");
    try (var files = Files.walk(databaseDirectory)) {
      for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(file);
      }
    }
  }

  @Test
  void guidedLoopResumesAndProvidesTargetedRetryFeedback() throws Exception {
    HttpResponse<String> page = get("/");
    assertEquals(200, page.statusCode());
    assertTrue(page.body().contains("type=\"module\""));
    assertTrue(page.body().contains("/app.js"));

    JSONObject catalog = new JSONObject(get("/api/catalog").body());
    assertTrue(catalog.has("next"));
    assertTrue(catalog.has("concepts"));
    assertTrue(catalog.has("summary"));
    boolean stackHasPrerequisite = false;
    for (Object value : catalog.getJSONArray("items")) {
      JSONObject item = (JSONObject) value;
      if ("int-stack".equals(item.getString("id"))
          && item.getJSONArray("prerequisites").toList().contains("dynamic-array")) {
        stackHasPrerequisite = true;
      }
    }
    assertTrue(stackHasPrerequisite);

    assertEquals(200, post("/api/session",
        new JSONObject().put("id", "hashmap-shape").put("step", "feedback")).statusCode());
    JSONObject next = new JSONObject(get("/api/next").body());
    assertEquals("resume", next.getString("kind"));
    assertEquals("hashmap-shape", next.getString("item"));
    assertEquals("feedback", next.getString("step"));

    JSONObject firstMiss = new JSONObject(post("/api/check",
        new JSONObject().put("id", "hashmap-shape").put("answer", "wrong").put("retry", false))
        .body());
    assertFalse(firstMiss.getBoolean("correct"));
    assertTrue(firstMiss.has("remediation"));
    assertTrue(firstMiss.has("retryPrompt"));
    assertFalse(firstMiss.getString("explanation").contains("Java 25 HashMap"));

    JSONObject retry = new JSONObject(post("/api/check",
        new JSONObject()
            .put("id", "hashmap-shape")
            .put("answer", "16 buckets and 0.75")
            .put("retry", true))
        .body());
    assertTrue(retry.getBoolean("correct"));
    assertTrue(retry.getString("explanation").contains("Java 25 HashMap"));
  }

  @Test
  void nextActionPrioritizesDueReview() throws Exception {
    assertEquals(200, post("/api/review",
        new JSONObject().put("id", "pair-indices").put("quality", 1)).statusCode());
    try (var connection = Store.open();
        var statement = connection.prepareStatement("update reviews set due=? where item=?")) {
      statement.setString(1, LocalDate.now().minusDays(1).toString());
      statement.setString(2, "pair-indices");
      statement.executeUpdate();
    }

    JSONObject next = new JSONObject(get("/api/next").body());
    assertEquals("review", next.getString("kind"));
    assertEquals("pair-indices", next.getString("item"));
  }

  @Test
  void apiErrorsAndModularAssetsRemainObservable() throws Exception {
    HttpResponse<String> script = get("/app.js");
    assertEquals(200, script.statusCode());
    assertTrue(script.headers().firstValue("content-type").orElse("").contains("javascript"));
    assertTrue(script.body().contains("draft-state.js"));

    HttpResponse<String> missingItem = post("/api/check",
        new JSONObject().put("id", "does-not-exist").put("answer", "x"));
    assertEquals(400, missingItem.statusCode());
    assertTrue(missingItem.body().contains("Unknown item"));

    HttpResponse<String> invalidStep = post("/api/session",
        new JSONObject().put("id", "hashmap-shape").put("step", "unknown"));
    assertEquals(400, invalidStep.statusCode());
    assertTrue(invalidStep.body().contains("Unknown session step"));

    HttpResponse<String> wrongMethod = get("/api/judge");
    assertEquals(405, wrongMethod.statusCode());
    assertTrue(wrongMethod.body().contains("Method not allowed"));
  }

  @Test
  void searchBookmarksAndProfileAreAvailable() throws Exception {
    HttpResponse<String> search = get("/api/search?q=HashMap");
    assertEquals(200, search.statusCode());
    assertTrue(search.body().contains("hashmap-shape"));

    JSONObject saved = new JSONObject(post("/api/bookmarks",
        new JSONObject().put("item", "hashmap-shape").put("saved", true)).body());
    assertTrue(saved.getJSONArray("items").toList().contains("hashmap-shape"));

    JSONObject profile = new JSONObject(post("/api/profile",
        new JSONObject().put("tier", "intermediate")
            .put("tracks", new org.json.JSONArray().put("Backend"))).body());
    assertEquals("intermediate", profile.getString("tier"));
  }

  @Test
  void deepBackendPathExposesVersionNotesAndScenarios() throws Exception {
    JSONObject catalog = new JSONObject(get("/api/catalog").body());
    boolean sawSpring = false;
    boolean sawScenario = false;
    for (Object value : catalog.getJSONArray("items")) {
      JSONObject item = (JSONObject) value;
      if ("boot-startup".equals(item.getString("id"))) {
        sawSpring = "experienced".equals(item.getString("tier"))
            && item.getString("bootBaseline").contains("Spring Boot 4");
      }
      if (item.has("scenario")) {
        sawScenario = item.getJSONObject("scenario").has("evidence")
            && item.getJSONObject("scenario").has("verification");
      }
    }
    assertTrue(sawSpring);
    assertTrue(sawScenario);
  }

  @Test
  void mixedAssessmentStartsAndResumes() throws Exception {
    JSONObject ready = new JSONObject(get("/api/assessment").body());
    assertTrue(Set.of("ready", "active", "complete").contains(ready.getString("status")));
    JSONObject started = new JSONObject(post("/api/assessment",
        new JSONObject().put("action", "start")).body());
    assertTrue(started.getJSONArray("items").length() >= 1);
    JSONObject resumed = new JSONObject(get("/api/assessment").body());
    assertEquals(started.getString("id"), resumed.getString("id"));
    if (started.getJSONArray("items").length() > 1) {
      JSONObject next = new JSONObject(post("/api/assessment",
          new JSONObject().put("action", "record").put("position", 0)
              .put("outcome", "pass")).body());
      assertEquals(1, next.getInt("position"));
    }
  }

  private HttpResponse<String> get(String path) throws Exception {
    return send("GET", path, null);
  }

  private HttpResponse<String> post(String path, JSONObject body) throws Exception {
    return send("POST", path, body.toString());
  }

  private HttpResponse<String> send(String method, String path, String body) throws Exception {
    HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve(path))
        .header("Content-Type", "application/json");
    if (cookie != null) request.header("Cookie", cookie);
    if ("POST".equals(method)) {
      request.POST(HttpRequest.BodyPublishers.ofString(body));
    } else {
      request.GET();
    }
    HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    response.headers().firstValue("set-cookie")
        .map(value -> value.split(";", 2)[0])
        .ifPresent(value -> cookie = value);
    return response;
  }
}
