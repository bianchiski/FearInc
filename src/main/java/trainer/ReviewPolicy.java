package trainer;

public final class ReviewPolicy {
  private ReviewPolicy() {}

  public static int forJudge(boolean passed, int hintsUsed) {
    if (!passed) return 1;
    if (hintsUsed <= 0) return 5;
    if (hintsUsed == 1) return 4;
    return 3;
  }

  public static int forAnswer(boolean correct) {
    return correct ? 5 : 1;
  }
}
