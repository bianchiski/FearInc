package trainer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public final class Main {
  private static final String SESSION_COOKIE = "trainerSession";
  private static final Map<String, Map<String, Integer>> HINTS_BY_SESSION = new ConcurrentHashMap<>();
  private static JSONObject catalog;

  public static void main(String[] args) throws Exception {
    if (args.length > 0 && "--self-check".equals(args[0])) {
      Store.init();
      catalog = Catalog.load(root().resolve("content"));
      Judge.selfCheck(catalog.getJSONArray("items"));
      System.out.println("Self-check passed for every reference solution.");
      return;
    }
    HttpServer server = createServer(8787);
    server.start();
    System.out.println("Interview Prep Trainer: http://localhost:8787");
    if (Boolean.getBoolean("trainer.openBrowser")) {
      openBrowser();
    }
  }

  static HttpServer createServer(int port) throws IOException {
    Store.init();
    catalog = Catalog.load(root().resolve("content"));
    HttpServer server = HttpServer.create(
        new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
    server.createContext("/", Main::page);
    server.createContext("/api/catalog", exchange -> api(exchange, Main::catalog));
    server.createContext("/api/next", exchange -> api(exchange, Main::next));
    server.createContext("/api/session", exchange -> api(exchange, Main::session));
    server.createContext("/api/judge", exchange -> api(exchange, Main::judge));
    server.createContext("/api/hint", exchange -> api(exchange, Main::hint));
    server.createContext("/api/check", exchange -> api(exchange, Main::check));
    server.createContext("/api/review", exchange -> api(exchange, Main::review));
    server.createContext("/api/due", exchange -> api(exchange, Main::due));
    server.createContext("/api/mistake", exchange -> api(exchange, Main::mistake));
    server.createContext("/api/settings", exchange -> api(exchange, Main::settings));
    server.createContext("/api/search", exchange -> api(exchange, Main::search));
    server.createContext("/api/bookmarks", exchange -> api(exchange, Main::bookmarks));
    server.createContext("/api/profile", exchange -> api(exchange, Main::profile));
    server.createContext("/api/assessment", exchange -> api(exchange, Main::assessment));
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    return server;
  }

  private interface ApiHandler {
    void handle(HttpExchange exchange) throws Exception;
  }

  private static void api(HttpExchange exchange, ApiHandler handler) throws IOException {
    sessionId(exchange);
    try {
      handler.handle(exchange);
    } catch (RequestHandledException ignored) {
      // The handler already sent the response.
    } catch (IllegalArgumentException | JSONException exception) {
      json(exchange, 400, new JSONObject()
          .put("error", exception.getMessage() == null ? "Invalid request" : exception.getMessage())
          .toString());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      json(exchange, 500, "{\"error\":\"Interrupted\"}");
    } catch (Exception exception) {
      json(exchange, 500, "{\"error\":\"Request failed\"}");
    }
  }

  private static void page(HttpExchange exchange) throws IOException {
    String name = "/".equals(exchange.getRequestURI().getPath()) ? "/index.html" : exchange.getRequestURI().getPath();
    Path webRoot = root().resolve("web");
    Path file = webRoot.resolve(name.substring(1)).normalize();
    if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
      json(exchange, 404, "{\"error\":\"Not found\"}");
      return;
    }
    byte[] body = Files.readAllBytes(file);
    String contentType = name.endsWith(".html") ? "text/html; charset=utf-8"
        : name.endsWith(".js") ? "application/javascript; charset=utf-8"
        : name.endsWith(".css") ? "text/css; charset=utf-8"
        : "text/plain; charset=utf-8";
    exchange.getResponseHeaders().set("Content-Type", contentType);
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream output = exchange.getResponseBody()) { output.write(body); }
  }

  private static void catalog(HttpExchange exchange) throws IOException {
    requireMethod(exchange, "GET");
    json(exchange, 200, publicCatalog().toString());
  }

  private static void due(HttpExchange exchange) throws IOException {
    requireMethod(exchange, "GET");
    json(exchange, 200, Store.due().toString());
  }

  private static void next(HttpExchange exchange) throws IOException {
    requireMethod(exchange, "GET");
    json(exchange, 200, nextAction(Store.settings().optBoolean("aiDrills", false)).toString());
  }

  private static void session(HttpExchange exchange) throws IOException {
    if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      json(exchange, 200, Store.sessionState().toString());
      return;
    }
    requireMethod(exchange, "POST");
    JSONObject request = body(exchange);
    String step = request.getString("step");
    if (!Set.of("teach", "recall", "practice", "feedback", "review", "complete").contains(step)) {
      throw new IllegalArgumentException("Unknown session step");
    }
    String id = request.optString("id", "");
    if ("complete".equals(step)) {
      id = null;
    } else {
      item(id);
    }
    Store.saveSession(id, step);
    json(exchange, 200, Store.sessionState().toString());
  }

  private static void judge(HttpExchange exchange) throws IOException, InterruptedException {
    requireMethod(exchange, "POST");
    JSONObject request = body(exchange);
    String id = request.getString("id");
    JSONObject item = item(id);
    String session = sessionId(exchange);
    int hints = hintsFor(session).getOrDefault(id, 0);
    JSONObject result;
    try {
      result = Judge.run(item, request.getString("source"));
    } finally {
      hintsFor(session).remove(id);
    }
    int quality = ReviewPolicy.forJudge(result.optBoolean("ok"), hints);
    JSONArray concepts = concepts(item);
    JSONObject review = Store.review(id, concepts, quality);
    Store.recordAttempt(id, concepts, "code", review.getInt("quality"), hints, result.optBoolean("ok"));
    result.put("review", review);
    result.put("nextStep", result.optBoolean("ok")
        ? "Explain the invariant, then complete the review."
        : "Review the highlighted feedback and retry.");
    if (result.optBoolean("ok") && item.has("solutionC")) {
      result.put("solutionC", item.getString("solutionC"));
    }
    json(exchange, 200, result.toString());
  }

  private static void hint(HttpExchange exchange) throws IOException {
    requireMethod(exchange, "POST");
    JSONObject request = body(exchange);
    String id = request.getString("id");
    JSONArray hints = item(id).getJSONArray("hints");
    Map<String, Integer> sessionHints = hintsFor(sessionId(exchange));
    synchronized (sessionHints) {
      int level = sessionHints.getOrDefault(id, 0);
      if (level >= hints.length()) throw new IllegalArgumentException("No more hints");
      sessionHints.put(id, level + 1);
      json(exchange, 200, new JSONObject()
          .put("hint", hints.getString(level))
          .put("level", level + 1)
          .put("total", hints.length())
          .toString());
    }
  }

  private static void check(HttpExchange exchange) throws IOException {
    requireMethod(exchange, "POST");
    JSONObject request = body(exchange);
    JSONObject item = item(request.getString("id"));
    String given = request.optString("answer", "").trim().toLowerCase();
    boolean correct = given.equals(item.getString("answer").trim().toLowerCase());
    boolean revealExplanation = correct || request.optBoolean("retry", false)
        || !item.has("misconceptions");
    String id = item.getString("id");
    JSONArray concepts = concepts(item);
    JSONObject review = Store.review(id, concepts, ReviewPolicy.forAnswer(correct));
    Store.recordAttempt(id, concepts, "recall", review.getInt("quality"), 0, correct);
    JSONObject response = new JSONObject()
        .put("correct", correct)
        .put("explanation", revealExplanation
            ? item.optString("explanation", "")
            : "Not yet. Use the retry prompt, then try the question once more.")
        .put("review", review);
    if (!correct) addRemediation(response, item);
    json(exchange, 200, response.toString());
  }

  private static void review(HttpExchange exchange) throws IOException {
    requireMethod(exchange, "POST");
    JSONObject request = body(exchange);
    String id = request.getString("id");
    JSONObject item = item(id);
    JSONArray concepts = concepts(item);
    JSONObject review = Store.review(id, concepts, request.getInt("quality"));
    Store.recordAttempt(id, concepts, "explain", review.getInt("quality"), 0,
        review.getInt("quality") >= 3);
    Store.saveSession(null, "complete");
    json(exchange, 200, review.toString());
  }

  private static void mistake(HttpExchange exchange) throws IOException {
    requireMethod(exchange, "POST");
    JSONObject request = body(exchange);
    String id = request.getString("id");
    item(id);
    Store.mistake(id, request.getString("mistake"));
    json(exchange, 200, new JSONObject().put("ok", true).put("weakSpots", Store.mistakes()).toString());
  }

  private static void settings(HttpExchange exchange) throws IOException {
    if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      JSONObject request = body(exchange);
      if (request.has("aiDrills")) Store.setFlag("aiDrills", request.getBoolean("aiDrills"));
    } else if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      requireMethod(exchange, "GET");
    }
    json(exchange, 200, Store.settings().toString());
  }

  private static void search(HttpExchange exchange) throws IOException {
    requireMethod(exchange, "GET");
    String query = exchange.getRequestURI().getQuery();
    String term = query == null ? "" : URLDecoder.decode(query.replaceFirst("^q=", ""),
        StandardCharsets.UTF_8).toLowerCase();
    JSONArray matches = new JSONArray();
    for (Object value : publicCatalog().getJSONArray("items")) {
      JSONObject item = (JSONObject) value;
      String haystack = item.toString().toLowerCase();
      if (term.isBlank() || haystack.contains(term)) matches.put(item);
    }
    json(exchange, 200, new JSONObject().put("items", matches).toString());
  }

  private static void bookmarks(HttpExchange exchange) throws IOException {
    if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      JSONObject request = body(exchange);
      Store.bookmark(request.getString("item"), request.optBoolean("saved"));
    } else {
      requireMethod(exchange, "GET");
    }
    json(exchange, 200, new JSONObject().put("items", Store.bookmarks()).toString());
  }

  private static void requireMethod(HttpExchange exchange, String method) throws IOException {
    if (method.equalsIgnoreCase(exchange.getRequestMethod())) return;
    exchange.getResponseHeaders().set("Allow", method);
    json(exchange, 405, "{\"error\":\"Method not allowed\"}");
    throw new RequestHandledException();
  }

  private static String sessionId(HttpExchange exchange) {
    String cookie = exchange.getRequestHeaders().getFirst("Cookie");
    if (cookie != null) {
      for (String part : cookie.split(";")) {
        String[] pair = part.trim().split("=", 2);
        if (pair.length == 2 && SESSION_COOKIE.equals(pair[0])) {
          try {
            String id = UUID.fromString(pair[1]).toString();
            HINTS_BY_SESSION.computeIfAbsent(id, ignored -> new ConcurrentHashMap<>());
            return id;
          } catch (IllegalArgumentException ignored) {
            break;
          }
        }
      }
    }
    String id = UUID.randomUUID().toString();
    HINTS_BY_SESSION.put(id, new ConcurrentHashMap<>());
    exchange.getResponseHeaders().set(
        "Set-Cookie", SESSION_COOKIE + "=" + id + "; Path=/; SameSite=Strict");
    return id;
  }

  private static Map<String, Integer> hintsFor(String session) {
    return HINTS_BY_SESSION.computeIfAbsent(session, ignored -> new ConcurrentHashMap<>());
  }

  private static JSONObject publicCatalog() {
    JSONArray items = new JSONArray();
    JSONObject settings = Store.settings();
    JSONArray masteredItems = Store.mastered();
    boolean aiDrills = settings.optBoolean("aiDrills", false);
    Set<String> mastered = new HashSet<>();
    for (Object value : masteredItems) mastered.add(String.valueOf(value));
    for (Object value : catalog.getJSONArray("items")) {
      JSONObject item = (JSONObject) value;
      if (item.optBoolean("aiStyled", false) && !aiDrills) continue;
      items.put(Catalog.redact(item, mastered.contains(item.getString("id"))));
    }
    return new JSONObject()
        .put("modules", catalog.getJSONArray("modules"))
        .put("items", items)
        .put("planner", catalog.getJSONObject("planner"))
        .put("due", Store.due())
        .put("mastered", masteredItems)
        .put("concepts", Store.conceptReadiness())
        .put("summary", Store.learningSummary())
        .put("session", Store.sessionState())
        .put("next", nextAction(aiDrills))
        .put("weakSpots", Store.mistakes())
        .put("settings", settings);
  }

  private static void profile(HttpExchange exchange) throws IOException {
    if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      JSONObject request = body(exchange);
      String tier = request.getString("tier");
      if (!Set.of("beginner", "intermediate", "experienced").contains(tier)) {
        throw new IllegalArgumentException("Invalid tier");
      }
      Store.saveProfile(tier, request.optJSONArray("tracks") == null
          ? new JSONArray().put("Backend") : request.getJSONArray("tracks"));
    } else {
      requireMethod(exchange, "GET");
    }
    json(exchange, 200, Store.profile().toString());
  }

  private static void assessment(HttpExchange exchange) throws IOException {
    if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      JSONObject state = Store.assessment();
      if ("none".equals(state.optString("status"))) {
        state = new JSONObject().put("status", "ready").put("plan", assessmentPlan());
      }
      json(exchange, 200, state.toString());
      return;
    }
    requireMethod(exchange, "POST");
    JSONObject request = body(exchange);
    String action = request.optString("action", "start");
    if ("start".equals(action)) {
      JSONObject plan = assessmentPlan();
      if (plan.getJSONArray("items").isEmpty()) {
        json(exchange, 200, new JSONObject().put("status", "unavailable").toString());
        return;
      }
      json(exchange, 200, Store.startAssessment(
          plan.getString("concept"), plan.getJSONArray("items")).toString());
      return;
    }
    JSONObject state = Store.assessment();
    if (!"active".equals(state.optString("status"))) {
      throw new IllegalArgumentException("No active assessment");
    }
    if ("record".equals(action)) {
      int position = request.getInt("position");
      JSONObject outcomes = state.getJSONObject("outcomes");
      outcomes.put(String.valueOf(position), request.getString("outcome"));
      json(exchange, 200, Store.saveAssessment(
          state.getString("id"), state.getString("concept"), state.getJSONArray("items"),
          Math.min(position + 1, state.getJSONArray("items").length()), outcomes, "active").toString());
      return;
    }
    if ("complete".equals(action)) {
      JSONObject outcomes = state.getJSONObject("outcomes");
      String result = assessmentResult(outcomes, state.getJSONArray("items").length());
      json(exchange, 200, Store.saveAssessment(
          state.getString("id"), state.getString("concept"), state.getJSONArray("items"),
          state.getInt("position"), outcomes.put("result", result), "complete")
          .put("result", result).toString());
      return;
    }
    throw new IllegalArgumentException("Unknown assessment action");
  }

  private static JSONObject assessmentPlan() {
    Set<String> weak = new HashSet<>();
    for (Object value : Store.mistakes()) weak.add(((JSONObject) value).getString("mistake"));
    List<JSONObject> candidates = new ArrayList<>();
    for (Object value : catalog.getJSONArray("items")) {
      JSONObject item = (JSONObject) value;
      if (!visible(item, Store.settings().optBoolean("aiDrills", false))
          || "lesson".equals(item.getString("type"))) continue;
      candidates.add(item);
    }
    candidates.sort(Comparator.comparing((JSONObject item) ->
        Store.due().toString().contains(item.getString("id")) ? 0 : 1)
        .thenComparing(item -> weak.isEmpty() ? 1 : 0));
    if (candidates.isEmpty()) return new JSONObject().put("concept", "").put("items", new JSONArray());
    JSONObject anchor = candidates.get(0);
    String concept = anchor.optString("concept", anchor.getString("id"));
    JSONArray selected = new JSONArray();
    for (JSONObject item : candidates) {
      if (!concept.equals(item.optString("concept", item.getString("id")))) continue;
      String type = item.getString("type");
      boolean wanted = selected.isEmpty() ? ("quiz".equals(type) || "drill".equals(type))
          : selected.length() == 1
              ? ("code-problem".equals(type) || "scenario".equals(type))
              : "explain".equals(type);
      if (wanted) selected.put(item.getString("id"));
      if (selected.length() == 3) break;
    }
    if (selected.length() == 0) selected.put(anchor.getString("id"));
    return new JSONObject().put("concept", concept).put("items", selected);
  }

  private static String assessmentResult(JSONObject outcomes, int total) {
    int score = 0;
    for (String key : outcomes.keySet()) {
      String outcome = outcomes.optString(key);
      if ("pass".equals(outcome) || "strong".equals(outcome)) score += 2;
      else if ("developing".equals(outcome)) score++;
    }
    if (score >= total * 2) return "strong";
    if (score > 0) return "developing";
    return "revisit";
  }

  private static Path root() {
    Path configured = Path.of(System.getProperty("trainer.root", ".")).toAbsolutePath().normalize();
    if (Files.isDirectory(configured.resolve("content"))) return configured;
    Path appDirectory = configured.resolve("app");
    if (Files.isDirectory(appDirectory.resolve("content"))) return appDirectory;
    return configured;
  }

  private static void openBrowser() {
    try {
      if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(URI.create("http://localhost:8787/"));
      }
    } catch (Exception ignored) {
      // Opening a browser is optional; the server remains available if it fails.
    }
  }

  private static JSONObject item(String id) {
    JSONObject item = findItem(id);
    if (item != null) return item;
    throw new IllegalArgumentException("Unknown item: " + id);
  }

  private static JSONObject findItem(String id) {
    for (Object value : catalog.getJSONArray("items")) {
      JSONObject item = (JSONObject) value;
      if (id.equals(item.getString("id"))) return item;
    }
    return null;
  }

  private static JSONArray concepts(JSONObject item) {
    if (item.has("concept")) return new JSONArray().put(item.getString("concept"));
    JSONArray concepts = item.optJSONArray("concepts");
    return concepts == null ? new JSONArray().put(item.getString("id"))
        : new JSONArray(concepts.toString());
  }

  private static void addRemediation(JSONObject response, JSONObject item) {
    JSONArray misconceptions = item.optJSONArray("misconceptions");
    if (misconceptions == null || misconceptions.isEmpty()) return;
    JSONObject misconception = misconceptions.getJSONObject(0);
    response.put("remediation", misconception.optString("explanation", ""));
    response.put("retryPrompt", misconception.optString("retry", ""));
  }

  private static JSONObject nextAction(boolean aiDrills) {
    JSONObject state = Store.sessionState();
    String stateItem = state.optString("item", "");
    String stateStep = state.optString("step", "");
    if (!stateItem.isBlank() && !"complete".equals(stateStep)) {
      JSONObject item = findItem(stateItem);
      if (item != null && visible(item, aiDrills)) {
        return action(item, stateStep, "resume",
            "Resume your unfinished " + item.getString("title") + ".");
      }
    }

    for (Object value : Store.due()) {
      JSONObject due = (JSONObject) value;
      JSONObject item = findItem(due.getString("item"));
      if (item != null && visible(item, aiDrills)) {
        return action(item, "teach", "review", "This item is due for review today.");
      }
    }

    Set<String> masteredItems = new HashSet<>();
    for (Object value : Store.mastered()) masteredItems.add(value.toString());
    Set<String> masteredConcepts = new HashSet<>();
    for (Object value : Store.conceptReadiness()) {
      JSONObject concept = (JSONObject) value;
      if ("mastered".equals(concept.getString("status"))) {
        masteredConcepts.add(concept.getString("concept"));
      }
    }
    for (Object moduleValue : catalog.getJSONArray("modules")) {
      JSONObject module = (JSONObject) moduleValue;
      for (Object idValue : module.getJSONArray("items")) {
        JSONObject item = findItem(idValue.toString());
        if (item != null && visible(item, aiDrills) && !"lesson".equals(item.getString("type"))
            && ready(item, masteredItems, masteredConcepts)) {
          return action(item, "teach", "new", "Next prerequisite-ready practice.");
        }
      }
    }

    for (Object value : catalog.getJSONArray("items")) {
      JSONObject item = (JSONObject) value;
      if (visible(item, aiDrills) && !"lesson".equals(item.getString("type"))) {
        return action(item, "teach", "preview", "Preview the next item while you build prerequisites.");
      }
    }
    return new JSONObject()
        .put("kind", "complete")
        .put("reason", "Everything currently visible is complete.");
  }

  private static boolean visible(JSONObject item, boolean aiDrills) {
    return !item.optBoolean("aiStyled", false) || aiDrills;
  }

  private static boolean ready(JSONObject item, Set<String> masteredItems, Set<String> masteredConcepts) {
    JSONArray prerequisites = item.optJSONArray("prerequisites");
    if (prerequisites == null) return true;
    for (Object value : prerequisites) {
      String prerequisite = value.toString();
      if (!masteredItems.contains(prerequisite) && !masteredConcepts.contains(prerequisite)) return false;
    }
    return true;
  }

  private static JSONObject action(JSONObject item, String step, String kind, String reason) {
    int minutes = "code-problem".equals(item.getString("type")) ? 20
        : "scenario".equals(item.getString("type")) ? 15 : 10;
    return new JSONObject()
        .put("kind", kind)
        .put("item", item.getString("id"))
        .put("title", item.getString("title"))
        .put("step", step)
        .put("reason", reason)
        .put("estimatedMinutes", minutes)
        .put("objective", item.optString("objective", item.getString("title")));
  }

  private static JSONObject body(HttpExchange exchange) throws IOException {
    byte[] bytes = exchange.getRequestBody().readAllBytes();
    if (bytes.length == 0) throw new IllegalArgumentException("Request body is required");
    return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
  }

  private static void json(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
  }

  private static final class RequestHandledException extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }
}
