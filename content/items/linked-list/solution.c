#include <stdlib.h> /* malloc allocates one node; free releases it. */

/* A node stores one value and the address of the following node. */
typedef struct Node {
  int value; /* value is the payload stored by this node. */
  struct Node *next; /* next points to the following node, or NULL at the tail. */
} Node;

void add_first(Node **head, int value) {
  Node *node = malloc(sizeof(*node)); /* Allocate memory for the new node. */
  node->value = value; /* Store the caller's value inside that node. */
  node->next = *head; /* Link the new node to the previous first node. */
  *head = node; /* Update head so the new node becomes first. */
}

int remove_first(Node **head, int *out) {
  if (*head == NULL) { /* An empty list has no node to remove. */
    return 0; /* Return failure without writing an output value. */
  }
  Node *old = *head; /* Save the address of the node being removed. */
  *out = old->value; /* Copy its value to the caller. */
  *head = old->next; /* Move head forward to the next node. */
  free(old); /* Release the removed node's heap memory. */
  return 1; /* Return success. */
}
