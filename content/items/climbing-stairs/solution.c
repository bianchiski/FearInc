/* Ways to climb exactly steps stairs, moving 1 or 2 steps at a time. */
int ways(int steps) {
  if (steps <= 1) return 1; /* Zero steps: do nothing. One step: a single move. */
  int previous = 1; /* ways(i-2) once the loop starts at i = 2. */
  int current = 1; /* ways(i-1). */
  for (int i = 2; i <= steps; i++) { /* Each step reuses the two answers before it. */
    int next = previous + current; /* Last move was 2 steps, or 1 step. */
    previous = current; /* Slide the window forward. */
    current = next;
  }
  return current; /* Only the last two states were stored. */
}
