/* Return the index of target, or -1 when target is absent.
   The array must be sorted in ascending order. */
int binary_search(const int *a, int n, int target) {
  int low = 0; /* low is the first index still inside the search range. */
  int high = n - 1; /* high is the last index still inside the search range. */
  while (low <= high) { /* Continue while the range contains at least one element. */
    int mid = low + (high - low) / 2; /* Calculate the midpoint without overflowing. */
    if (a[mid] == target) { /* The midpoint stores the requested value. */
      return mid; /* Return its zero-based index. */
    }
    if (a[mid] < target) { /* The target can only exist to the right. */
      low = mid + 1; /* Discard mid and everything before it. */
    } else { /* Otherwise the target can only exist to the left. */
      high = mid - 1; /* Discard mid and everything after it. */
    }
  }
  return -1; /* The range became empty, so the target was not found. */
}
