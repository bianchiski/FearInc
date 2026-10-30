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
      "id", "module", "type", "title", "sources", "verified");

  private Catalog() {}

  public static JSONObject load(Path root) throws IOException {
    JSONArray items = new JSONArray();
    try (var files = Files.walk(root.resolve("items"))) {
      files.filter(path -> path.getFileName().toString().equals("item.json"))
          .sorted(Comparator.comparing(Path::toString))
          .forEach(path -> items.put(readItem(path)));
    }
    validate(items);
    JSONObject modules = new JSONObject(Files.readString(root.resolve("catalog.json")));
    JSONArray moduleList = modules.getJSONArray("modules");
    validateSyllabus(root, moduleList, items);
    return new JSONObject()
        .put("modules", moduleList)
        .put("items", items)
        .put("planner", new JSONObject(Files.readString(root.resolve("planner.json"))));
  }

  public static JSONObject redact(JSONObject item, boolean solved) {
    JSONObject copy = new JSONObject(item.toString());
    copy.remove("reference");
    copy.remove("tests");
    copy.remove("answer");
    if (!solved) copy.remove("solutionC");
    if (copy.has("hints")) copy.put("hintCount", copy.getJSONArray("hints").length()).remove("hints");
    if ("drill".equals(copy.optString("type")) && copy.optBoolean("aiStyled", false)) copy.put("optionalAi", true);
    return copy;
  }

  private static JSONObject readItem(Path path) {
    try {
      JSONObject item = new JSONObject(Files.readString(path));
      Path directory = path.getParent();
      if (item.has("theory")) {
        StringBuilder theoryText = new StringBuilder();
        for (Object topic : item.getJSONArray("theory")) {
          Path theory = directory.getParent().getParent()
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
      for (String field : REQUIRED) {
        if (!item.has(field) || item.get(field).toString().isBlank()) {
          throw new IllegalStateException(item.optString("id", "unknown") + " is missing " + field);
        }
      }
      if (!item.has("theory") || item.getJSONArray("theory").isEmpty()
          || !item.has("theoryText") || item.getString("theoryText").isBlank()) {
        throw new IllegalStateException(item.getString("id") + " needs beginner-friendly theory");
      }
      if (SOURCE_REQUIRED.contains(item.getString("module")) && item.getJSONArray("sources").isEmpty()) {
        throw new IllegalStateException(item.getString("id") + " requires sources");
      }
      if ("code-problem".equals(item.getString("type"))) {
        if (item.getJSONArray("tests").length() < 6) throw new IllegalStateException(item.getString("id") + " needs 6 tests");
        if (item.getJSONArray("hints").length() < 3) throw new IllegalStateException(item.getString("id") + " needs 3 hints");
        if (item.getJSONArray("think").length() < 4) throw new IllegalStateException(item.getString("id") + " needs a walkthrough");
        if (!item.has("className") || item.getString("className").isBlank()) {
          throw new IllegalStateException(item.getString("id") + " needs a className");
        }
        if (!item.has("reference") || !item.has("complexity")) {
          throw new IllegalStateException(item.getString("id") + " needs a reference and complexity");
        }
      }
    }
  }

  private static void validateSyllabus(Path root, JSONArray modules, JSONArray items) throws IOException {
    Map<String, JSONObject> byId = new HashMap<>();
    for (Object value : items) {
      JSONObject item = (JSONObject) value;
      byId.put(item.getString("id"), item);
    }
    Set<String> listed = new HashSet<>();
    Set<String> moduleIds = new HashSet<>();
    for (Object value : modules) {
      JSONObject module = (JSONObject) value;
      String moduleId = module.getString("id");
      if (!moduleIds.add(moduleId)) throw new IllegalStateException("Duplicate module " + moduleId);
      if (!module.has("items")) throw new IllegalStateException(moduleId + " needs an items list");
      for (Object idValue : module.getJSONArray("items")) {
        String id = idValue.toString();
        if (!listed.add(id)) throw new IllegalStateException(id + " is listed twice");
        JSONObject item = byId.get(id);
        if (item == null) throw new IllegalStateException(moduleId + " lists missing item " + id);
        if (!moduleId.equals(item.getString("module"))) {
          throw new IllegalStateException(id + " is listed under " + moduleId + " but belongs to " + item.getString("module"));
        }
      }
    }
    for (String id : byId.keySet()) {
      if (!listed.contains(id)) throw new IllegalStateException(id + " is on disk but missing from the syllabus");
    }
    Path modulesDir = root.resolve("modules");
    if (!Files.isDirectory(modulesDir)) return;
    try (var files = Files.walk(modulesDir)) {
      files.filter(path -> path.getFileName().toString().equals("module.json"))
          .forEach(path -> checkModuleFile(path, byId));
    }
  }

  private static void checkModuleFile(Path path, Map<String, JSONObject> byId) {
    try {
      JSONObject module = new JSONObject(Files.readString(path));
      if (!module.has("problems")) return;
      String moduleId = module.getString("id");
      for (Object value : module.getJSONArray("problems")) {
        String id = ((JSONObject) value).getString("id");
        JSONObject item = byId.get(id);
        if (item == null) throw new IllegalStateException(path + " lists missing item " + id);
        if (!moduleId.equals(item.getString("module"))) {
          throw new IllegalStateException(id + " in " + path + " belongs to " + item.getString("module"));
        }
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Could not read " + path, exception);
    }
  }
}
