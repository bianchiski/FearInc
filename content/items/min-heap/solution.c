/* Insert value while preserving the min-heap property.
   Every parent must be less than or equal to both children. */
void heap_push(int heap[], int *size, int value) {
  int i = *size; /* i is the first unused index in the heap array. */
  (*size)++; /* Reserve that index by increasing the logical heap size. */
  heap[i] = value; /* Place the new value at the bottom of the heap. */
  while (i > 0) { /* Stop when the value reaches the root. */
    int parent = (i - 1) / 2; /* Integer division finds this node's parent index. */
    if (heap[parent] <= heap[i]) { /* The min-heap property already holds. */
      break; /* No more swaps are needed. */
    }
    int temp = heap[parent]; /* Save the parent before swapping. */
    heap[parent] = heap[i]; /* Move the smaller child upward. */
    heap[i] = temp; /* Move the larger parent downward. */
    i = parent; /* Continue checking from the child's new position. */
  }
}
