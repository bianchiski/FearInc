package trainer;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.json.JSONArray;
import org.json.JSONObject;

public final class Judge {
  private Judge() {}

  public static JSONObject run(JSONObject item, String source) throws IOException, InterruptedException {
    String className = item.getString("className");
    if (!declaresClass(source, className)) {
      return failure("The submitted file does not declare class " + className + ".");
    }
    Path directory = Files.createTempDirectory("trainer-judge-");
    try {
      Files.writeString(directory.resolve(className + ".java"), source);
      Path resultFile = directory.resolve("results.txt");
      Path stdoutFile = directory.resolve("stdout.txt");
      Path stderrFile = directory.resolve("stderr.txt");
      Files.writeString(directory.resolve("Harness.java"), harness(item));
      JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
      if (compiler == null) return failure("No Java compiler is available. Run the trainer with a JDK, not a JRE.");
      ByteArrayOutputStream errors = new ByteArrayOutputStream();
      int compiled = compiler.run(null, null, errors, "-d", directory.toString(),
          directory.resolve(className + ".java").toString(), directory.resolve("Harness.java").toString());
      if (compiled != 0) return failure(errors.toString(StandardCharsets.UTF_8));

      Process process = new ProcessBuilder(javaExecutable(), "-Xmx128m", "-cp", directory.toString(),
          "Harness", resultFile.toString())
          .redirectOutput(stdoutFile.toFile())
          .redirectError(stderrFile.toFile())
          .start();
      boolean finished = process.waitFor(5, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        process.waitFor(2, TimeUnit.SECONDS);
        return failure("Time limit exceeded");
      }
      JSONArray tests = parseResults(resultFile);
      int passed = 0;
      for (Object value : tests) {
        JSONObject test = (JSONObject) value;
        boolean ok = test.getBoolean("passed");
        if (ok) passed++;
        JSONObject definition = testDefinition(item, test.getString("name"));
        if (definition != null) {
          copyIfPresent(test, definition, "concept");
          copyIfPresent(test, definition, "edgeCase");
          JSONObject feedback = item.optJSONObject("feedback");
          if (feedback != null && feedback.has(test.getString("name"))) {
            test.put("feedback", feedback.getString(test.getString("name")));
          }
        }
      }
      JSONObject result = new JSONObject()
          .put("ok", passed == item.getJSONArray("tests").length() && tests.length() == item.getJSONArray("tests").length())
          .put("passed", passed)
          .put("total", item.getJSONArray("tests").length())
          .put("tests", tests)
          .put("stderr", readText(stderrFile));
      if (tests.isEmpty() && process.exitValue() != 0) {
        result.put("error", "The test harness exited before reporting results.");
      }
      return result;
    } finally {
      deleteTree(directory);
    }
  }

  private static String javaExecutable() {
    String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
        ? "java.exe" : "java";
    return Path.of(System.getProperty("java.home"), "bin", executable).toString();
  }

  private static JSONArray parseResults(Path resultFile) throws IOException {
    JSONArray tests = new JSONArray();
    if (!Files.exists(resultFile)) return tests;
    for (String line : Files.readAllLines(resultFile, StandardCharsets.UTF_8)) {
      String[] parts = line.split("\t", 3);
      if (parts.length < 2 || (!parts[0].equals("PASS") && !parts[0].equals("FAIL"))) continue;
      boolean passed = parts[0].equals("PASS");
      JSONObject test = new JSONObject().put("name", parts[1]).put("passed", passed);
      if (parts.length > 2) test.put("detail", parts[2]);
      tests.put(test);
    }
    return tests;
  }

  private static JSONObject testDefinition(JSONObject item, String name) {
    for (Object value : item.getJSONArray("tests")) {
      JSONObject definition = (JSONObject) value;
      if (name.equals(definition.optString("name"))) return definition;
    }
    return null;
  }

  private static void copyIfPresent(JSONObject target, JSONObject source, String field) {
    if (source.has(field)) target.put(field, source.get(field));
  }

  private static String readText(Path path) throws IOException {
    return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
  }

  private static boolean declaresClass(String source, String className) {
    return Pattern.compile("\\bclass\\s+" + Pattern.quote(className) + "\\b").matcher(source).find();
  }

  private static void deleteTree(Path root) {
    try {
      List<Path> paths;
      try (var walk = Files.walk(root)) {
        paths = walk.sorted(Comparator.reverseOrder()).toList();
      }
      for (Path path : paths) Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // A leftover temp directory is less important than the judge result.
    }
  }

  private static JSONObject failure(String message) {
    return new JSONObject().put("ok", false).put("passed", 0).put("total", 0).put("error", message).put("tests", new JSONArray());
  }

  private static String harness(JSONObject item) {
    StringBuilder checks = new StringBuilder();
    for (Object value : item.getJSONArray("tests")) {
      JSONObject test = (JSONObject) value;
      checks.append("check(results,")
          .append(javaString(test.getString("name"))).append(",() -> ")
          .append(test.getString("call")).append(',')
          .append(javaString(test.getString("expect"))).append(");\n");
    }
    return """
        public class Harness {
          static int failed;
          interface Case { String run() throws Exception; }
          static void check(java.io.PrintWriter results, String name, Case test, String expected) {
            try {
              String actual = test.run();
              if (java.util.Objects.equals(actual, expected)) results.println("PASS\\t" + name);
              else { failed++; results.println("FAIL\\t" + name + "\\texpected " + expected + " but was " + actual); }
            } catch (Throwable error) {
              failed++;
              results.println("FAIL\\t" + name + "\\t" + error);
            }
          }
          public static void main(String[] args) throws Exception {
            try (java.io.PrintWriter results = new java.io.PrintWriter(
                java.nio.file.Files.newBufferedWriter(
                    java.nio.file.Path.of(args[0]), java.nio.charset.StandardCharsets.UTF_8))) {
            %s
            }
            if (failed > 0) System.exit(2);
          }
        }
        """.formatted(checks);
  }

  private static String javaString(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
  }

  public static void selfCheck(JSONArray items) throws IOException, InterruptedException {
    int failures = 0;
    for (Object value : items) {
      JSONObject item = (JSONObject) value;
      if (!"code-problem".equals(item.getString("type"))) continue;
      JSONObject result = run(item, item.getString("reference"));
      if (!result.optBoolean("ok")) {
        failures++;
        System.err.println(item.getString("id") + " reference failed: " + result);
      }
    }
    if (failures > 0) throw new IllegalStateException(failures + " reference solutions failed");
  }
}
