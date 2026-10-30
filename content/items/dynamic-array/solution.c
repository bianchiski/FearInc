#include <stdlib.h> /* realloc changes the size of heap memory. */

/* size is the number of stored values; capacity is the number of allocated slots. */
typedef struct {
  int *data; /* data points to the contiguous integer storage. */
  int size; /* size counts values currently in use. */
  int capacity; /* capacity counts allocated slots, including unused ones. */
} IntArray;

void push(IntArray *v, int value) {
  if (v->size == v->capacity) { /* The buffer is full and must grow. */
    v->capacity = v->capacity ? v->capacity * 2 : 4; /* Double it, or start at four slots. */
    v->data = realloc(v->data, v->capacity * sizeof(int)); /* Request a larger contiguous block. */
  }
  v->data[v->size] = value; /* Write into the first unused slot. */
  v->size++; /* Increase the logical length after the write succeeds. */
}
