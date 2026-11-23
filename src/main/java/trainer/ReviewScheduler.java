package trainer;

import java.time.LocalDate;

public final class ReviewScheduler {
  private static final double MIN_EASINESS = 1.3;

  private ReviewScheduler() {}

  public record State(double easiness, int intervalDays, int repetitions) {}

  public record NextReview(double easiness, int intervalDays, int repetitions,
                           int quality, LocalDate due) {}

  public static NextReview next(State previous, int requestedQuality, LocalDate today) {
    int quality = Math.max(0, Math.min(5, requestedQuality));
    double easiness = previous == null ? 2.5 : previous.easiness();
    int interval = previous == null ? 0 : previous.intervalDays();
    int repetitions = previous == null ? 0 : previous.repetitions();

    if (quality < 3) {
      repetitions = 0;
      interval = 1;
    } else {
      repetitions++;
      interval = repetitions == 1 ? 1 : repetitions == 2 ? 6
          : (int) Math.round(interval * easiness);
    }

    easiness = Math.max(MIN_EASINESS,
        easiness + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)));
    return new NextReview(
        easiness,
        interval,
        repetitions,
        quality,
        today.plusDays(interval));
  }
}
