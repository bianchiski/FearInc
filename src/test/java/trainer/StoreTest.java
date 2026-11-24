package trainer;

import java.nio.file.Files;
import java.nio.file.Path;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StoreTest {
  private Path databaseDirectory;

  @BeforeEach
  void setUp() throws Exception {
    databaseDirectory = Files.createTempDirectory("trainer-store-test-");
    System.setProperty("trainer.db", databaseDirectory.resolve("trainer.db").toString());
    Store.init();
  }

  @AfterEach
  void tearDown() throws Exception {
    System.clearProperty("trainer.db");
    try (var files = Files.walk(databaseDirectory)) {
      for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(file);
      }
    }
  }

  @Test
  void masteryComesOnlyFromReviewQuality() {
    Store.review("failed-code", ReviewPolicy.forJudge(false, 0));
    Store.review("hinted-code", ReviewPolicy.forJudge(true, 2));
    Store.review("solved-quiz", ReviewPolicy.forAnswer(true));

    JSONArray mastered = Store.mastered();
    assertFalse(mastered.toList().contains("failed-code"));
    assertFalse(mastered.toList().contains("hinted-code"));
    assertTrue(mastered.toList().contains("solved-quiz"));
  }

  @Test
  void conceptReviewsAndSessionStatePersistLearningContext() throws Exception {
    JSONArray concepts = new JSONArray().put("stack LIFO invariant").put("array capacity and order");
    Store.review("int-stack", concepts, 5);
    Store.recordAttempt("int-stack", concepts, "code", 5, 0, true);
    Store.saveSession("int-stack", "feedback");

    assertTrue(Store.sessionState().getString("item").equals("int-stack"));
    assertTrue(Store.sessionState().getString("step").equals("feedback"));
    try (var connection = Store.open();
        var statement = connection.createStatement();
        var rows = statement.executeQuery("select count(*) from learning_attempts")) {
      assertTrue(rows.next());
      assertEquals(2, rows.getInt(1));
    }
    assertTrue(Store.conceptReadiness().toList().stream()
        .map(Object::toString)
        .anyMatch(value -> value.contains("stack LIFO invariant")));
    assertTrue(Store.learningSummary().getInt("conceptsMastered") >= 2);
    assertTrue(Store.learningSummary().getInt("streakDays") >= 1);
  }

  @Test
  void activeAssessmentPersistsAndCanBeResumed() {
    JSONArray items = new JSONArray().put("hashmap-shape").put("explain-pair-indices");
    JSONObject started = Store.startAssessment("hashmap", items);
    assertEquals("active", started.getString("status"));
    JSONObject resumed = Store.assessment();
    assertEquals(started.getString("id"), resumed.getString("id"));
    assertEquals(0, resumed.getInt("position"));
    assertEquals(2, resumed.getJSONArray("items").length());
  }
}
