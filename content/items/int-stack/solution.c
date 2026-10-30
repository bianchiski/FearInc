#define CAPACITY 100 /* CAPACITY is the fixed maximum number of stored integers. */

/* top counts stored values and is also the next free slot. */
typedef struct {
  int data[CAPACITY]; /* data is the stack's contiguous storage. */
  int top; /* top equals zero when the stack is empty. */
} Stack;

int push(Stack *s, int value) {
  if (s->top == CAPACITY) { /* The fixed array has no unused slot. */
    return 0; /* Reject the push instead of writing past the array. */
  }
  s->data[s->top] = value; /* Store the value at the current top index. */
  s->top++; /* Advance top to the next free slot. */
  return 1; /* Report that the push succeeded. */
}

int pop(Stack *s, int *out) {
  if (s->top == 0) { /* There is no value available to remove. */
    return 0; /* Return failure without changing the stack. */
  }
  s->top--; /* Move top back to the last stored value. */
  *out = s->data[s->top]; /* Copy that value through the output pointer. */
  return 1; /* Report that the pop succeeded. */
}
