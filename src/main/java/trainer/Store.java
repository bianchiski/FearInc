package trainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;

import org.json.JSONArray;
import org.json.JSONObject;

public final class Store {
  private Store() {}

  public static Connection open() throws SQLException {
    return DriverManager.getConnection("jdbc:sqlite:trainer.db");
  }

  public static void init() {
    try (Connection connection = open(); Statement statement = connection.createStatement()) {
      statement.executeUpdate("drop table if exists progress");
      statement.executeUpdate("create table if not exists attempts(id integer primary key, item text, outcome text, mistake text, created text)");
      statement.executeUpdate("create table if not exists settings(name text primary key, value text not null)");
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
          create table if not exists judge_runs(
            id integer primary key,
            item text not null,
            passed integer not null,
            total integer not null,
            hints_used integer not null,
            created text not null)
          """);
      try (PreparedStatement insert = connection.prepareStatement(
          "insert or ignore into settings(name,value) values(?,?)")) {
        insert.setString(1, "aiDrills");
        insert.setString(2, "false");
        insert.executeUpdate();
        insert.setString(1, "aiFeedback");
        insert.setString(2, "false");
        insert.executeUpdate();
      }
    } catch (SQLException exception) {
      throw new IllegalStateException("Could not initialize the local database", exception);
    }
  }

  public static JSONObject settings() {
    JSONObject settings = new JSONObject();
    try (Connection connection = open();
        ResultSet rows = connection.createStatement().executeQuery("select name,value from settings")) {
      while (rows.next()) {
        settings.put(rows.getString(1), Boolean.parseBoolean(rows.getString(2)));
      }
    } catch (SQLException exception) {
      throw new IllegalStateException(exception);
    }
    return settings;
  }

  public static void setFlag(String name, boolean value) {
    try (Connection connection = open();
        PreparedStatement statement = connection.prepareStatement(
            "insert or replace into settings(name,value) values(?,?)")) {
      statement.setString(1, name);
      statement.setString(2, Boolean.toString(value));
      statement.executeUpdate();
    } catch (SQLException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public static void recordJudge(String item, int passed, int total, int hintsUsed) {
    try (Connection connection = open();
        PreparedStatement statement = connection.prepareStatement(
            "insert into judge_runs(item,passed,total,hints_used,created) values(?,?,?,?,?)")) {
      statement.setString(1, item);
      statement.setInt(2, passed);
      statement.setInt(3, total);
      statement.setInt(4, hintsUsed);
      statement.setString(5, Instant.now().toString());
      statement.executeUpdate();
    } catch (SQLException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public static JSONObject review(String item, int quality) {
    quality = Math.max(0, Math.min(5, quality));
    double easiness = 2.5;
    int interval = 0;
    int repetitions = 0;
    try (Connection connection = open()) {
      try (PreparedStatement select = connection.prepareStatement(
          "select easiness,interval_days,repetitions from reviews where item=?")) {
        select.setString(1, item);
        try (ResultSet row = select.executeQuery()) {
          if (row.next()) {
            easiness = row.getDouble(1);
            interval = row.getInt(2);
            repetitions = row.getInt(3);
          }
        }
      }
      if (quality < 3) {
        repetitions = 0;
        interval = 1;
      } else {
        repetitions++;
        interval = repetitions == 1 ? 1 : repetitions == 2 ? 6 : (int) Math.round(interval * easiness);
      }
      easiness = easiness + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
      if (easiness < 1.3) easiness = 1.3;
      String due = LocalDate.now().plusDays(interval).toString();
      try (PreparedStatement upsert = connection.prepareStatement("""
          insert into reviews(item,easiness,interval_days,repetitions,due,quality)
          values(?,?,?,?,?,?)
          on conflict(item) do update set
            easiness=excluded.easiness,
            interval_days=excluded.interval_days,
            repetitions=excluded.repetitions,
            due=excluded.due,
            quality=excluded.quality
          """)) {
        upsert.setString(1, item);
        upsert.setDouble(2, easiness);
        upsert.setInt(3, interval);
        upsert.setInt(4, repetitions);
        upsert.setString(5, due);
        upsert.setInt(6, quality);
        upsert.executeUpdate();
      }
      return new JSONObject().put("item", item).put("quality", quality).put("due", due).put("intervalDays", interval);
    } catch (SQLException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public static JSONArray due() {
    JSONArray items = new JSONArray();
    try (Connection connection = open();
        PreparedStatement statement = connection.prepareStatement(
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
    } catch (SQLException exception) {
      throw new IllegalStateException(exception);
    }
    return items;
  }

  public static void mistake(String item, String mistake) {
    try (Connection connection = open();
        PreparedStatement statement = connection.prepareStatement(
            "insert into attempts(item,outcome,mistake,created) values(?,?,?,?)")) {
      statement.setString(1, item);
      statement.setString(2, "review");
      statement.setString(3, mistake);
      statement.setString(4, Instant.now().toString());
      statement.executeUpdate();
    } catch (SQLException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public static JSONArray mastered() {
    JSONArray items = new JSONArray();
    try (Connection connection = open();
        ResultSet rows = connection.createStatement().executeQuery(
            "select item from reviews where quality >= 4 union select item from judge_runs where passed = total and total > 0")) {
      while (rows.next()) items.put(rows.getString(1));
    } catch (SQLException exception) {
      throw new IllegalStateException(exception);
    }
    return items;
  }

  public static JSONArray mistakes() {
    JSONArray items = new JSONArray();
    try (Connection connection = open();
        ResultSet rows = connection.createStatement().executeQuery(
            "select mistake,count(*) from attempts where mistake<>'' group by mistake order by count(*) desc")) {
      while (rows.next()) {
        items.put(new JSONObject().put("mistake", rows.getString(1)).put("count", rows.getInt(2)));
      }
    } catch (SQLException exception) {
      throw new IllegalStateException(exception);
    }
    return items;
  }
}
