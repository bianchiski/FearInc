package trainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

public final class Store {
  @FunctionalInterface
  private interface SqlWork<T> {
    T run(Connection connection) throws SQLException;
  }

  @FunctionalInterface
  private interface SqlAction {
    void run(Connection connection) throws SQLException;
  }

  private Store() {}

  public static Connection open() throws SQLException {
    return DriverManager.getConnection("jdbc:sqlite:" + databasePath());
  }

  private static String databasePath() {
    String configured = System.getProperty("trainer.db");
    if (configured != null && !configured.isBlank()) return configured;
    if (!Boolean.getBoolean("trainer.packaged")) return "trainer.db";
    String appData = System.getenv("APPDATA");
    if (appData == null || appData.isBlank()) return "trainer.db";
    Path directory = Path.of(appData, "FearInc");
    try {
      Files.createDirectories(directory);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to create FearInc application data directory", exception);
    }
    return directory.resolve("trainer.db").toString();
  }

  private static <T> T withConnection(SqlWork<T> work) {
    try (Connection connection = open()) {
      return work.run(connection);
    } catch (SQLException exception) {
      throw new IllegalStateException("Database operation failed", exception);
    }
  }

  private static void withConnection(SqlAction action) {
    try (Connection connection = open()) {
      action.run(connection);
    } catch (SQLException exception) {
      throw new IllegalStateException("Database operation failed", exception);
    }
  }

  public static void init() {
    withConnection(connection -> {
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate("""
            create table if not exists attempts(
              id integer primary key,
              item text not null,
              mistake text not null,
              created text not null)
            """);
        statement.executeUpdate(
            "create table if not exists settings(name text primary key, value text not null)");
        statement.executeUpdate("""
            create table if not exists reviews(
              item text primary key,
              easiness real not null,
              interval_days integer not null,
              repetitions integer not null,
              due text not null,
              quality integer not null)
            """);
      statement.executeUpdate("""
          create table if not exists concept_reviews(
            concept text primary key,
            easiness real not null,
            interval_days integer not null,
            repetitions integer not null,
            due text not null,
            quality integer not null)
          """);
      statement.executeUpdate("""
          create table if not exists learning_attempts(
            id integer primary key,
            item text not null,
            concept text not null,
            activity text not null,
            quality integer not null,
            hints_used integer not null,
            completed integer not null,
            created text not null)
          """);
      statement.executeUpdate("""
          create table if not exists session_state(
            id integer primary key check(id = 1),
            item text,
            step text not null,
            started text not null,
            updated text not null)
          """);
      }
      try (PreparedStatement insert = connection.prepareStatement(
          "insert or ignore into settings(name,value) values(?,?)")) {
        insert.setString(1, "aiDrills");
        insert.setString(2, "false");
        insert.executeUpdate();
      }
    });
  }

  public static JSONObject settings() {
    return withConnection(connection -> {
      JSONObject settings = new JSONObject();
      try (Statement statement = connection.createStatement();
          ResultSet rows = statement.executeQuery("select name,value from settings")) {
        while (rows.next()) {
          settings.put(rows.getString(1), Boolean.parseBoolean(rows.getString(2)));
        }
      }
      return settings;
    });
  }

  public static void setFlag(String name, boolean value) {
    withConnection(connection -> {
      try (PreparedStatement statement = connection.prepareStatement(
          "insert or replace into settings(name,value) values(?,?)")) {
        statement.setString(1, name);
        statement.setString(2, Boolean.toString(value));
        statement.executeUpdate();
      }
    });
  }

  public static JSONObject review(String item, int quality) {
    return review(item, new JSONArray().put(item), quality);
  }

  public static JSONObject review(String item, JSONArray concepts, int quality) {
    return withConnection(connection -> {
      ReviewScheduler.NextReview next = schedule(connection, "reviews", "item", item, quality);
      JSONArray scheduledConcepts = concepts.length() == 0
          ? new JSONArray().put(item) : concepts;
      for (Object value : scheduledConcepts) {
        String concept = value.toString().trim();
        if (!concept.isEmpty()) {
          schedule(connection, "concept_reviews", "concept", concept, quality);
        }
      }
      return new JSONObject()
          .put("item", item)
          .put("quality", next.quality())
          .put("due", next.due())
          .put("intervalDays", next.intervalDays());
    });
  }

  private static ReviewScheduler.NextReview schedule(
      Connection connection, String table, String keyColumn, String key, int quality)
      throws SQLException {
    ReviewScheduler.State previous = null;
    try (PreparedStatement select = connection.prepareStatement(
        "select easiness,interval_days,repetitions from " + table + " where " + keyColumn + "=?")) {
      select.setString(1, key);
      try (ResultSet row = select.executeQuery()) {
        if (row.next()) {
          previous = new ReviewScheduler.State(row.getDouble(1), row.getInt(2), row.getInt(3));
        }
      }
    }

    ReviewScheduler.NextReview next = ReviewScheduler.next(previous, quality, LocalDate.now());
    try (PreparedStatement upsert = connection.prepareStatement(
        "insert into " + table + "(" + keyColumn
            + ",easiness,interval_days,repetitions,due,quality) values(?,?,?,?,?,?) "
            + "on conflict(" + keyColumn + ") do update set "
            + "easiness=excluded.easiness, interval_days=excluded.interval_days, "
            + "repetitions=excluded.repetitions, due=excluded.due, quality=excluded.quality")) {
      upsert.setString(1, key);
      upsert.setDouble(2, next.easiness());
      upsert.setInt(3, next.intervalDays());
      upsert.setInt(4, next.repetitions());
      upsert.setString(5, next.due().toString());
      upsert.setInt(6, next.quality());
      upsert.executeUpdate();
    }
    return next;
  }

  public static void recordAttempt(
      String item, JSONArray concepts, String activity, int quality, int hintsUsed, boolean completed) {
    withConnection(connection -> {
      try (PreparedStatement insert = connection.prepareStatement("""
          insert into learning_attempts(
            item,concept,activity,quality,hints_used,completed,created)
          values(?,?,?,?,?,?,?)
          """)) {
        JSONArray recordedConcepts = concepts.length() == 0
            ? new JSONArray().put(item) : concepts;
        for (Object value : recordedConcepts) {
          String concept = value.toString().trim();
          if (concept.isEmpty()) continue;
          insert.setString(1, item);
          insert.setString(2, concept);
          insert.setString(3, activity);
          insert.setInt(4, Math.max(0, Math.min(5, quality)));
          insert.setInt(5, Math.max(0, hintsUsed));
          insert.setInt(6, completed ? 1 : 0);
          insert.setString(7, Instant.now().toString());
          insert.addBatch();
        }
        insert.executeBatch();
      }
    });
  }

  public static JSONArray conceptReadiness() {
    return withConnection(connection -> {
      JSONArray concepts = new JSONArray();
      String today = LocalDate.now().toString();
      try (Statement statement = connection.createStatement();
          ResultSet rows = statement.executeQuery(
              "select concept,due,quality from concept_reviews order by due,concept")) {
        while (rows.next()) {
          String due = rows.getString(2);
          int quality = rows.getInt(3);
          String status = due.compareTo(today) <= 0 ? "review"
              : quality >= 4 ? "mastered" : "learning";
          concepts.put(new JSONObject()
              .put("concept", rows.getString(1))
              .put("due", due)
              .put("quality", quality)
              .put("status", status));
        }
      }
      return concepts;
    });
  }

  public static JSONObject learningSummary() {
    return withConnection(connection -> {
      int mastered = 0;
      int due = 0;
      for (Object value : conceptReadiness()) {
        JSONObject concept = (JSONObject) value;
        if ("mastered".equals(concept.getString("status"))) mastered++;
        if ("review".equals(concept.getString("status"))) due++;
      }

      Set<LocalDate> completedDays = new HashSet<>();
      try (Statement statement = connection.createStatement();
          ResultSet rows = statement.executeQuery(
              "select substr(created,1,10) from learning_attempts where completed=1")) {
        while (rows.next()) completedDays.add(LocalDate.parse(rows.getString(1)));
      }
      int streak = 0;
      LocalDate day = LocalDate.now();
      while (completedDays.contains(day)) {
        streak++;
        day = day.minusDays(1);
      }
      return new JSONObject()
          .put("conceptsMastered", mastered)
          .put("conceptsToReview", due)
          .put("streakDays", streak);
    });
  }

  public static JSONObject sessionState() {
    return withConnection(connection -> {
      JSONObject state = new JSONObject();
      try (PreparedStatement statement = connection.prepareStatement(
          "select item,step,started,updated from session_state where id=1");
          ResultSet row = statement.executeQuery()) {
        if (row.next()) {
          if (row.getString(1) != null) state.put("item", row.getString(1));
          state.put("step", row.getString(2));
          state.put("started", row.getString(3));
          state.put("updated", row.getString(4));
        }
      }
      return state;
    });
  }

  public static void saveSession(String item, String step) {
    withConnection(connection -> {
      String now = Instant.now().toString();
      try (PreparedStatement statement = connection.prepareStatement("""
          insert into session_state(id,item,step,started,updated)
          values(1,?,?,?,?)
          on conflict(id) do update set
            item=excluded.item,
            step=excluded.step,
            updated=excluded.updated
          """)) {
        if (item == null) statement.setNull(1, Types.VARCHAR);
        else statement.setString(1, item);
        statement.setString(2, step);
        statement.setString(3, now);
        statement.setString(4, now);
        statement.executeUpdate();
      }
    });
  }

  public static JSONArray due() {
    return withConnection(connection -> {
      JSONArray items = new JSONArray();
      try (PreparedStatement statement = connection.prepareStatement(
          "select item,due,quality from reviews where due<=? order by due")) {
        statement.setString(1, LocalDate.now().toString());
        try (ResultSet rows = statement.executeQuery()) {
          while (rows.next()) {
            items.put(new JSONObject()
                .put("item", rows.getString(1))
                .put("due", rows.getString(2))
                .put("quality", rows.getInt(3)));
          }
        }
      }
      return items;
    });
  }

  public static void mistake(String item, String mistake) {
    withConnection(connection -> {
      try (PreparedStatement statement = connection.prepareStatement(
          "insert into attempts(item,mistake,created) values(?,?,?)")) {
        statement.setString(1, item);
        statement.setString(2, mistake);
        statement.setString(3, Instant.now().toString());
        statement.executeUpdate();
      }
    });
  }

  public static JSONArray mastered() {
    return withConnection(connection -> {
      JSONArray items = new JSONArray();
      try (Statement statement = connection.createStatement();
          ResultSet rows = statement.executeQuery(
              "select item from reviews where quality >= 4 order by item")) {
        while (rows.next()) items.put(rows.getString(1));
      }
      return items;
    });
  }

  public static JSONArray mistakes() {
    return withConnection(connection -> {
      JSONArray items = new JSONArray();
      try (Statement statement = connection.createStatement();
          ResultSet rows = statement.executeQuery(
              "select mistake,count(*) from attempts where mistake<>'' group by mistake order by count(*) desc")) {
        while (rows.next()) {
          items.put(new JSONObject().put("mistake", rows.getString(1)).put("count", rows.getInt(2)));
        }
      }
      return items;
    });
  }
}
