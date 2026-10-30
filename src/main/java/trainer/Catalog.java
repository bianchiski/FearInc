package trainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
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
    return new JSONObject()
        .put("modules", modules.getJSONArray("modules"))
        .put("items", items)
        .put("planner", new JSONObject(Files.readString(root.resolve("planner.json"))));
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
        if (!item.has("reference") || !item.has("complexity")) {
          throw new IllegalStateException(item.getString("id") + " needs a reference and complexity");
        }
      }
    }
  }
}
