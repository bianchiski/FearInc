/* Return 1 when any value appears twice, otherwise return 0.
   This simple version uses constant extra memory and quadratic time. */
int has_duplicate(const int *a, int n) {
  for (int i = 0; i < n; i++) { /* Choose the first element of a comparison. */
    for (int j = i + 1; j < n; j++) { /* Compare it with every later element. */
      if (a[i] == a[j]) { /* Equal values mean a duplicate exists. */
        return 1; /* Stop immediately after finding the first duplicate. */
      }
    }
  }
  return 0; /* Every unique pair was different. */
}
