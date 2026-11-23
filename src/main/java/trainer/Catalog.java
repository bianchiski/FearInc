package trainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

public final class Catalog {
  private static final Set<String> SOURCE_REQUIRED = Set.of("aws", "security", "spring");
  private static final List<String> REQUIRED = List.of(
      "id", "type", "title", "verified");

  private Catalog() {}

  public static JSONObject load(Path root) throws IOException {
    JSONArray items = readItems(root);
    JSONObject syllabus = new JSONObject(Files.readString(root.resolve("catalog.json")));
    JSONArray moduleList = requiredArray(syllabus, "modules", "catalog");
    validateSyllabus(moduleList, items);
    validate(items);
    return new JSONObject()
        .put("modules", moduleList)
        .put("items", items)
        .put("planner", new JSONObject(Files.readString(root.resolve("planner.json"))));
  }

  private static JSONArray readItems(Path root) throws IOException {
    JSONArray items = new JSONArray();
    try (var files = Files.walk(root.resolve("items"))) {
      files.filter(path -> path.getFileName().toString().equals("item.json"))
          .sorted(Comparator.comparing(Path::toString))
          .forEach(path -> items.put(readItem(root, path)));
    }
    return items;
  }

  public static JSONObject redact(JSONObject item, boolean solved) {
    JSONObject copy = new JSONObject(item.toString());
    copy.remove("reference");
    copy.remove("tests");
    copy.remove("answer");
    if (!solved) copy.remove("solutionC");
    copy.remove("hints");
    copy.remove("feedback");
    copy.remove("misconceptions");
    copy.remove("retryPrompt");
    return copy;
  }

  private static JSONObject readItem(Path root, Path path) {
    try {
      JSONObject item = new JSONObject(Files.readString(path));
      Path directory = path.getParent();
      if (item.has("theory")) {
        StringBuilder theoryText = new StringBuilder();
        for (Object topic : item.getJSONArray("theory")) {
          Path theory = root
              .resolve("theory").resolve(topic.toString()).resolve("theory.md");
          if (Files.exists(theory)) {
            if (theoryText.length() > 0) theoryText.append("\n\n---\n\n");
            theoryText.append(Files.readString(theory));
          }
        }
        if (theoryText.length() > 0) item.put("theoryText", theoryText.toString());
      }
      Path reference = directory.resolve("reference.java");
      Path solutionC = directory.resolve("solution.c");
      Path starter = directory.resolve("starter.java");
      if (Files.exists(reference)) item.put("reference", Files.readString(reference));
      if (Files.exists(solutionC)) item.put("solutionC", Files.readString(solutionC));
      if (Files.exists(starter)) item.put("starter", Files.readString(starter));
      return item;
    } catch (IOException exception) {
      throw new IllegalStateException("Could not read " + path, exception);
    }
  }

  private static void validate(JSONArray items) {
    for (Object value : items) {
      JSONObject item = (JSONObject) value;
      String id = item.optString("id", "unknown");
      for (String field : REQUIRED) {
        requiredString(item, field, id);
      }
      requiredString(item, "track", id);
      requiredTier(item, id);
      requiredArray(item, "tags", id);
      requiredObject(item, "eli5", id);
      requiredString(item.getJSONObject("eli5"), "analogy", id + " eli5");
      requiredString(item.getJSONObject("eli5"), "definition", id + " eli5");
      requiredString(item.getJSONObject("eli5"), "example", id + " eli5");
      requiredString(item.getJSONObject("eli5"), "limits", id + " eli5");
      requiredObject(item, "interview", id);
      JSONObject interview = item.getJSONObject("interview");
      requiredString(interview, "question", id + " interview");
      requiredString(interview, "shortAnswer", id + " interview");
      requiredString(interview, "deepAnswer", id + " interview");
      requiredArray(item, "tips", id);
      requiredArray(item, "commonMistakes", id);
      requiredArray(item, "followUps", id);
      JSONArray sources = requiredArray(item, "sources", id);
      JSONArray theory = requiredArray(item, "theory", id);
      if (theory.isEmpty() || item.optString("theoryText").isBlank()) {
        throw new IllegalStateException(id + " needs beginner-friendly theory");
      }
      if (SOURCE_REQUIRED.contains(item.getString("module"))
          && sources.isEmpty()) {
        throw new IllegalStateException(id + " requires sources");
      }
      if ("code-problem".equals(item.getString("type"))) {
        JSONArray tests = requiredArray(item, "tests", id);
        if (tests.length() < 6) {
          throw new IllegalStateException(id + " needs 6 tests");
        }
        if (requiredArray(item, "hints", id).length() < 3) {
          throw new IllegalStateException(id + " needs 3 hints");
        }
        if (requiredArray(item, "think", id).length() < 4) {
          throw new IllegalStateException(id + " needs a walkthrough");
        }
        requiredString(item, "className", id);
        if (!item.has("reference") || !item.has("complexity")) {
          throw new IllegalStateException(id + " needs a reference and complexity");
        }
        if ("data-structures".equals(item.getString("module"))) {
          validateInstructionalFields(item, tests, id);
        }
      } else if ("quiz".equals(item.getString("type")) || "drill".equals(item.getString("type"))) {
        requiredString(item, "answer", id);
        if ("data-structures".equals(item.getString("module")) && item.has("misconceptions")) {
          validateMisconceptions(item, id);
        }
      }
    }
  }

  private static void requiredTier(JSONObject item, String id) {
    String tier = requiredString(item, "tier", id);
    if (!Set.of("beginner", "intermediate", "experienced").contains(tier)) {
      throw new IllegalStateException(id + " has an invalid tier");
    }
  }

  private static void validateInstructionalFields(JSONObject item, JSONArray tests, String id) {
    requiredString(item, "objective", id);
    requiredArray(item, "prerequisites", id);
    requiredString(item, "recallPrompt", id);
    if (requiredArray(item, "workedExample", id).length() < 2) {
      throw new IllegalStateException(id + " needs a two-step worked example");
    }
    validateMisconceptions(item, id);
    JSONObject feedback = requiredObject(item, "feedback", id);
    requiredString(item, "reflectionPrompt", id);
    for (Object value : tests) {
      if (!(value instanceof JSONObject test)) {
        throw new IllegalStateException(id + " has an invalid test");
      }
      String name = requiredString(test, "name", id + " test");
      requiredString(test, "concept", id + " test " + name);
      requiredString(test, "edgeCase", id + " test " + name);
      requiredString(test, "call", id + " test " + name);
      if (!(test.opt("expect") instanceof String)) {
        throw new IllegalStateException(id + " test " + name + " is missing expect");
      }
      requiredString(feedback, name, id + " feedback");
    }
  }

  private static void validateMisconceptions(JSONObject item, String id) {
    JSONArray misconceptions = requiredArray(item, "misconceptions", id);
    if (misconceptions.length() < 2) {
      throw new IllegalStateException(id + " needs two misconceptions");
    }
    for (Object value : misconceptions) {
      if (!(value instanceof JSONObject misconception)) {
        throw new IllegalStateException(id + " has an invalid misconception");
      }
      requiredString(misconception, "id", id + " misconception");
      requiredString(misconception, "explanation", id + " misconception");
      requiredString(misconception, "retry", id + " misconception");
    }
  }

  private static void validateSyllabus(JSONArray modules, JSONArray items) {
    Map<String, JSONObject> byId = new HashMap<>();
    for (Object value : items) {
      JSONObject item = (JSONObject) value;
      String id = requiredString(item, "id", "item");
      if (byId.put(id, item) != null) throw new IllegalStateException("Duplicate item " + id);
    }
    Set<String> listed = new HashSet<>();
    Set<String> moduleIds = new HashSet<>();
    for (Object value : modules) {
      JSONObject module = (JSONObject) value;
      String moduleId = requiredString(module, "id", "module");
      if (!moduleIds.add(moduleId)) throw new IllegalStateException("Duplicate module " + moduleId);
      requiredString(module, "title", moduleId);
      for (Object idValue : requiredArray(module, "items", moduleId)) {
        String id = idValue.toString();
        if (!listed.add(id)) throw new IllegalStateException(id + " is listed twice");
        JSONObject item = byId.get(id);
        if (item == null) throw new IllegalStateException(moduleId + " lists missing item " + id);
        item.put("module", moduleId);
      }
    }
    for (String id : byId.keySet()) {
      if (!listed.contains(id)) throw new IllegalStateException(id + " is on disk but missing from the syllabus");
    }
  }

  private static String requiredString(JSONObject object, String field, String owner) {
    Object value = object.opt(field);
    if (!(value instanceof String text) || text.isBlank()) {
      throw new IllegalStateException(owner + " is missing " + field);
    }
    return text;
  }

  private static JSONArray requiredArray(JSONObject object, String field, String owner) {
    Object value = object.opt(field);
    if (!(value instanceof JSONArray array)) {
      throw new IllegalStateException(owner + " is missing " + field);
    }
    return array;
  }

  private static JSONObject requiredObject(JSONObject object, String field, String owner) {
    Object value = object.opt(field);
    if (!(value instanceof JSONObject result)) {
      throw new IllegalStateException(owner + " is missing " + field);
    }
    return result;
  }
}
