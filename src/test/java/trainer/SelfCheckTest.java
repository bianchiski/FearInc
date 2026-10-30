package trainer;

import java.nio.file.Path;

import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SelfCheckTest {
  @Test
  void referenceSolutionsPassAndSolutionsStayHidden() throws Exception {
    JSONObject catalog = Catalog.load(Path.of("content"));
    JSONArray items = catalog.getJSONArray("items");
    Judge.selfCheck(items);
    boolean sawCode = false;
    for (Object value : items) {
      JSONObject item = (JSONObject) value;
      JSONObject hidden = Catalog.redact(item, false);
      assertFalse(hidden.has("solutionC"), item.getString("id"));
      assertFalse(hidden.has("reference"), item.getString("id"));
      assertFalse(hidden.has("tests"), item.getString("id"));
      assertFalse(hidden.has("answer"), item.getString("id"));
      if (!"code-problem".equals(item.getString("type"))) continue;
      sawCode = true;
      assertTrue(item.has("solutionC"), item.getString("id"));
      assertTrue(Catalog.redact(item, true).has("solutionC"), item.getString("id"));
      JSONObject rejected = Judge.run(item, "class Wrong {}");
      assertFalse(rejected.optBoolean("ok"), item.getString("id"));
    }
    assertTrue(sawCode);
  }
}
