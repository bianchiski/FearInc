#define CAPACITY 100 /* CAPACITY is the maximum number of queued integers. */

/* head is the index of the oldest value; size is the number of stored values. */
typedef struct {
  int data[CAPACITY]; /* data is the circular buffer. */
  int head; /* head advances when a value leaves the queue. */
  int size; /* size tracks occupancy independently of head. */
} Queue;

int enqueue(Queue *q, int value) {
  if (q->size == CAPACITY) { /* Every slot is already occupied. */
    return 0; /* Reject the value instead of overwriting old data. */
  }
  int tail = (q->head + q->size) % CAPACITY; /* Wrap the insertion index around the array. */
  q->data[tail] = value; /* Store the newest value at the logical tail. */
  q->size++; /* Increase the number of occupied slots. */
  return 1; /* Report success. */
}

int dequeue(Queue *q, int *out) {
  if (q->size == 0) { /* The queue contains no value. */
    return 0; /* Return failure without writing output. */
  }
  *out = q->data[q->head]; /* Copy the oldest value to the caller. */
  q->head = (q->head + 1) % CAPACITY; /* Move head forward, wrapping at the end. */
  q->size--; /* Decrease the number of occupied slots. */
  return 1; /* Report success. */
}
