package trainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.json.JSONArray;
import org.json.JSONObject;

public final class Judge {
  private static final List<String> BLOCKED = List.of(
      "Runtime.getRuntime", "ProcessBuilder", "System.exit", "java.lang.reflect", "Unsafe");

  private Judge() {}

  public static JSONObject run(JSONObject item, String source) throws IOException, InterruptedException {
    for (String blocked : BLOCKED) {
      if (source.contains(blocked)) {
        return failure("Rejected local-only safeguard: " + blocked);
      }
    }
    if (!source.contains("class Solution") && !source.contains("class DynamicArray")
        && !source.contains("class LinkedList") && !source.contains("class IntStack")
        && !source.contains("class IntQueue") && !source.contains("class OpenMap")
        && !source.contains("class MinHeap") && !source.contains("class Bst")) {
      return failure("The submitted file does not declare the required class.");
    }
    Path directory = Files.createTempDirectory("trainer-judge-");
    String className = item.getString("className");
    Files.writeString(directory.resolve(className + ".java"), source);
    Files.writeString(directory.resolve("Harness.java"), harness(item));
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) return failure("No Java compiler is available. Run the trainer with a JDK, not a JRE.");
    java.io.ByteArrayOutputStream errors = new java.io.ByteArrayOutputStream();
    int compiled = compiler.run(null, null, errors, "-d", directory.toString(),
        directory.resolve(className + ".java").toString(), directory.resolve("Harness.java").toString());
    if (compiled != 0) return failure(errors.toString(StandardCharsets.UTF_8));

    Process process = new ProcessBuilder("java", "-Xmx128m", "-cp", directory.toString(), "Harness")
        .redirectErrorStream(false).start();
    boolean finished = process.waitFor(5, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      process.waitFor(2, TimeUnit.SECONDS);
      return failure("Time limit exceeded");
    }
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    JSONArray tests = new JSONArray();
    int passed = 0;
    for (String line : output.split("\\R")) {
      String[] parts = line.split("\t", 4);
      if (parts.length < 2 || (!parts[0].equals("PASS") && !parts[0].equals("FAIL"))) continue;
      boolean ok = parts[0].equals("PASS");
      if (ok) passed++;
      JSONObject test = new JSONObject().put("name", parts[1]).put("passed", ok);
      if (parts.length > 2) test.put("detail", parts[2]);
      tests.put(test);
    }
    return new JSONObject()
        .put("ok", passed == item.getJSONArray("tests").length() && tests.length() == item.getJSONArray("tests").length())
        .put("passed", passed)
        .put("total", item.getJSONArray("tests").length())
        .put("tests", tests)
        .put("stderr", error);
  }

  private static JSONObject failure(String message) {
    return new JSONObject().put("ok", false).put("passed", 0).put("total", 0).put("error", message).put("tests", new JSONArray());
  }

  private static String harness(JSONObject item) {
    StringBuilder checks = new StringBuilder();
    for (Object value : item.getJSONArray("tests")) {
      JSONObject test = (JSONObject) value;
      checks.append("check(")
          .append(javaString(test.getString("name"))).append(",() -> ")
          .append(test.getString("call")).append(',')
          .append(javaString(test.getString("expect"))).append(");\n");
    }
    return """
        public class Harness {
          static int failed;
          interface Case { String run() throws Exception; }
          static void check(String name, Case test, String expected) {
            try {
              String actual = test.run();
              if (java.util.Objects.equals(actual, expected)) System.out.println("PASS\\t" + name);
              else { failed++; System.out.println("FAIL\\t" + name + "\\texpected " + expected + " but was " + actual); }
            } catch (Throwable error) {
              failed++;
              System.out.println("FAIL\\t" + name + "\\t" + error);
            }
          }
          public static void main(String[] args) {
            %s
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
