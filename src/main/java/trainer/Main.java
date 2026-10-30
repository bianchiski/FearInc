package trainer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public final class Main {
  private static JSONObject catalog;

  public static void main(String[] args) throws Exception {
    Store.init();
    catalog = Catalog.load(Path.of("content"));
    if (args.length > 0 && "--self-check".equals(args[0])) {
      Judge.selfCheck(catalog.getJSONArray("items"));
      System.out.println("Self-check passed for every reference solution.");
      return;
    }
    HttpServer server = HttpServer.create(new InetSocketAddress(8787), 0);
    server.createContext("/", Main::page);
    server.createContext("/api/catalog", exchange -> json(exchange, 200, publicCatalog().toString()));
    server.createContext("/api/judge", Main::judge);
    server.createContext("/api/hint", Main::hint);
    server.createContext("/api/check", Main::check);
    server.createContext("/api/review", Main::review);
    server.createContext("/api/due", exchange -> json(exchange, 200, Store.due().toString()));
    server.createContext("/api/mistake", Main::mistake);
    server.createContext("/api/settings", Main::settings);
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    server.start();
    System.out.println("Interview Prep Trainer: http://localhost:8787");
  }

  private static void page(HttpExchange exchange) throws IOException {
    String name = "/".equals(exchange.getRequestURI().getPath()) ? "/index.html" : exchange.getRequestURI().getPath();
    Path file = Path.of("web").resolve(name.substring(1)).normalize();
    if (!file.startsWith(Path.of("web")) || !Files.exists(file)) {
      json(exchange, 404, "{\"error\":\"Not found\"}");
      return;
    }
    byte[] body = Files.readAllBytes(file);
    exchange.getResponseHeaders().set("Content-Type", name.endsWith(".html") ? "text/html; charset=utf-8" : "text/plain");
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream output = exchange.getResponseBody()) { output.write(body); }
  }

  private static void judge(HttpExchange exchange) throws IOException {
    JSONObject request = body(exchange);
    JSONObject item = item(request.getString("id"));
    try {
      JSONObject result = Judge.run(item, request.getString("source"));
      int hints = request.optInt("hintsUsed", 0);
      if (result.optBoolean("ok")) Store.recordJudge(item.getString("id"), result.getInt("passed"), result.getInt("total"), hints);
      int quality = !result.optBoolean("ok") ? 1 : hints == 0 ? 5 : hints == 1 ? 4 : 3;
      result.put("review", Store.review(item.getString("id"), quality));
      json(exchange, 200, result.toString());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      json(exchange, 500, "{\"error\":\"Interrupted\"}");
    }
  }

  private static void hint(HttpExchange exchange) throws IOException {
    JSONObject request = body(exchange);
    JSONArray hints = item(request.getString("id")).getJSONArray("hints");
    int level = request.getInt("level");
    if (level < 0 || level >= hints.length()) { json(exchange, 400, "{\"error\":\"No such hint\"}"); return; }
    json(exchange, 200, new JSONObject().put("hint", hints.getString(level)).put("level", level + 1).put("total", hints.length()).toString());
  }

  private static void check(HttpExchange exchange) throws IOException {
    JSONObject request = body(exchange);
    JSONObject item = item(request.getString("id"));
    String given = request.optString("answer", "").trim().toLowerCase();
    boolean correct = given.equals(item.getString("answer").trim().toLowerCase());
    if (correct) Store.review(item.getString("id"), 5);
    json(exchange, 200, new JSONObject().put("correct", correct).put("explanation", item.optString("explanation", "")).toString());
  }

  private static void review(HttpExchange exchange) throws IOException {
    JSONObject request = body(exchange);
    json(exchange, 200, Store.review(request.getString("id"), request.getInt("quality")).toString());
  }

  private static void mistake(HttpExchange exchange) throws IOException {
    JSONObject request = body(exchange);
    Store.mistake(request.getString("id"), request.getString("mistake"));
    json(exchange, 200, new JSONObject().put("ok", true).put("weakSpots", Store.mistakes()).toString());
  }

  private static void settings(HttpExchange exchange) throws IOException {
    if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      JSONObject request = body(exchange);
      if (request.has("aiDrills")) Store.setFlag("aiDrills", request.getBoolean("aiDrills"));
      if (request.has("aiFeedback")) Store.setFlag("aiFeedback", request.getBoolean("aiFeedback"));
    }
    json(exchange, 200, Store.settings().toString());
  }

  private static JSONObject publicCatalog() {
    JSONArray items = new JSONArray();
    boolean aiDrills = Store.settings().optBoolean("aiDrills", false);
    for (Object value : catalog.getJSONArray("items")) {
      JSONObject item = new JSONObject(value.toString());
      if (item.optBoolean("aiStyled", false) && !aiDrills) continue;
      item.remove("reference");
      item.remove("tests");
      item.remove("answer");
      if (item.has("hints")) item.put("hintCount", item.getJSONArray("hints").length()).remove("hints");
      if ("drill".equals(item.optString("type")) && item.optBoolean("aiStyled", false)) item.put("optionalAi", true);
      items.put(item);
    }
    return new JSONObject()
        .put("modules", catalog.getJSONArray("modules"))
        .put("items", items)
        .put("planner", catalog.getJSONObject("planner"))
        .put("due", Store.due())
        .put("mastered", Store.mastered())
        .put("weakSpots", Store.mistakes())
        .put("settings", Store.settings());
  }

  private static JSONObject item(String id) {
    for (Object value : catalog.getJSONArray("items")) {
      JSONObject item = (JSONObject) value;
      if (id.equals(item.getString("id"))) return item;
    }
    throw new IllegalArgumentException("Unknown item: " + id);
  }

  private static JSONObject body(HttpExchange exchange) throws IOException {
    return new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
  }

  private static void json(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
  }
}
