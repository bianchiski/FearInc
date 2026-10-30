class Solution {
  public int ways(int steps) {
    if (steps <= 1) return 1;
    int previous = 1;
    int current = 1;
    for (int i = 2; i <= steps; i++) {
      int next = previous + current;
      previous = current;
      current = next;
    }
    return current;
  }
}
