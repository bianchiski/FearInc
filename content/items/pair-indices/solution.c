#include <stdlib.h> /* malloc and free come from the C standard library. */

/* Return an array containing two indices whose values add to target.
   The caller owns the returned memory and must call free when finished. */
int *two_sum(const int *a, int n, int target, int *out_count) {
  int *answer = malloc(2 * sizeof(int)); /* Allocate exactly two integer slots. */
  for (int i = 0; i < n; i++) { /* i is the first index in the current pair. */
    for (int j = i + 1; j < n; j++) { /* j starts after i, so pairs are unique. */
      if (a[i] + a[j] == target) { /* Compare this pair's sum with the target. */
        answer[0] = i; /* Store the first matching index. */
        answer[1] = j; /* Store the second matching index. */
        *out_count = 2; /* Tell the caller that two results were written. */
        return answer; /* Return ownership of the allocated result array. */
      }
    }
  }
  *out_count = 0; /* No matching pair exists. */
  free(answer); /* Release the unused allocation before returning. */
  return NULL; /* NULL means the search found no answer. */
}
