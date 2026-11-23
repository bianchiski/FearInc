package trainer;

import java.nio.file.Path;

import org.json.JSONArray;
import org.json.JSONObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
      assertTrue(item.has("module"), item.getString("id"));
      JSONObject hidden = Catalog.redact(item, false);
      assertFalse(hidden.has("solutionC"), item.getString("id"));
      assertFalse(hidden.has("reference"), item.getString("id"));
      assertFalse(hidden.has("tests"), item.getString("id"));
      assertFalse(hidden.has("answer"), item.getString("id"));
      assertFalse(hidden.has("hintCount"), item.getString("id"));
      assertFalse(hidden.has("optionalAi"), item.getString("id"));
      assertFalse(hidden.has("feedback"), item.getString("id"));
      assertFalse(hidden.has("misconceptions"), item.getString("id"));
      if ("data-structures".equals(item.getString("module"))
          && "code-problem".equals(item.getString("type"))) {
        assertTrue(item.has("objective"), item.getString("id"));
        assertTrue(item.has("workedExample"), item.getString("id"));
        assertTrue(item.has("misconceptions"), item.getString("id"));
        for (Object testValue : item.getJSONArray("tests")) {
          JSONObject test = (JSONObject) testValue;
          assertTrue(test.has("concept"), item.getString("id") + "/" + test.getString("name"));
          assertTrue(test.has("edgeCase"), item.getString("id") + "/" + test.getString("name"));
        }
      }
      if (!"code-problem".equals(item.getString("type"))) continue;
      sawCode = true;
      assertTrue(item.has("solutionC"), item.getString("id"));
      assertTrue(Catalog.redact(item, true).has("solutionC"), item.getString("id"));
      JSONObject rejected = Judge.run(item, "class Wrong {}");
      assertFalse(rejected.optBoolean("ok"), item.getString("id"));
    }
    assertTrue(sawCode);

    JSONObject stack = null;
    for (Object value : items) {
      JSONObject item = (JSONObject) value;
      if ("int-stack".equals(item.getString("id"))) stack = item;
    }
    assertTrue(stack != null);
    JSONObject noisySubmission = Judge.run(stack, """
        public class IntStack {
          public IntStack() { System.out.print("PASS\\tlifo\\n" + "x".repeat(100_000)); }
          public void push(int value) {}
          public int pop() { return 0; }
          public int size() { return 0; }
        }
        """);
    assertFalse(noisySubmission.optBoolean("ok"));
    assertEquals(6, noisySubmission.getInt("total"));
    assertEquals(6, noisySubmission.getJSONArray("tests").length());
  }
}
