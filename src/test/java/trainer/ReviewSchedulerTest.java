package trainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ReviewSchedulerTest {
  @Test
  void judgeQualityReflectsHintsAndFailures() {
    assertEquals(5, ReviewPolicy.forJudge(true, 0));
    assertEquals(4, ReviewPolicy.forJudge(true, 1));
    assertEquals(3, ReviewPolicy.forJudge(true, 2));
    assertEquals(1, ReviewPolicy.forJudge(false, 0));
  }

  @Test
  void scheduleKeepsTheSpacedRepetitionInvariants() {
    LocalDate today = LocalDate.of(2026, 11, 23);
    ReviewScheduler.NextReview first = ReviewScheduler.next(null, 5, today);
    assertEquals(1, first.intervalDays());
    assertEquals(1, first.repetitions());
    assertEquals(today.plusDays(1), first.due());

    ReviewScheduler.NextReview second = ReviewScheduler.next(
        new ReviewScheduler.State(first.easiness(), first.intervalDays(), first.repetitions()),
        4,
        today);
    assertEquals(6, second.intervalDays());
    assertEquals(2, second.repetitions());

    ReviewScheduler.NextReview failed = ReviewScheduler.next(
        new ReviewScheduler.State(second.easiness(), second.intervalDays(), second.repetitions()),
        1,
        today);
    assertEquals(1, failed.intervalDays());
    assertEquals(0, failed.repetitions());
    assertTrue(failed.easiness() >= 1.3);
  }
}
