/* Return the largest sum of any contiguous window with the given width. */
int max_window_sum(const int *a, int n, int width) {
  if (width <= 0 || width > n) { /* Reject an impossible window size. */
    return 0; /* There is no valid window to sum. */
  }
  int sum = 0; /* sum stores the current window total. */
  for (int i = 0; i < width; i++) { /* Build the first window explicitly. */
    sum += a[i]; /* Add each value inside that first window. */
  }
  int best = sum; /* The first window is the best result seen so far. */
  for (int right = width; right < n; right++) { /* right is the new window's last index. */
    sum += a[right]; /* Include the element entering from the right. */
    sum -= a[right - width]; /* Remove the element leaving from the left. */
    if (sum > best) { /* Compare the moved window with the recorded maximum. */
      best = sum; /* Keep the larger total. */
    }
  }
  return best; /* Return the maximum window sum. */
}
