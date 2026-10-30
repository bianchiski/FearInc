class Solution {
  public int maxWindow(int[] values, int width) {
    int sum = 0;
    for (int i = 0; i < width; i++) sum += values[i];
    int best = sum;
    for (int i = width; i < values.length; i++) {
      sum += values[i] - values[i - width];
      if (sum > best) best = sum;
    }
    return best;
  }
}
