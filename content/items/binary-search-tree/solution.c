/* A binary search tree node has one value and two child pointers. */
typedef struct Tree {
  int value; /* value is the key stored at this node. */
  struct Tree *left; /* left points to the subtree of smaller keys. */
  struct Tree *right; /* right points to the subtree of larger keys. */
} Tree;

int contains(Tree *node, int target) {
  if (node == NULL) { /* This branch is empty. */
    return 0; /* The target is not present on this path. */
  }
  if (node->value == target) { /* The current node stores the target key. */
    return 1; /* Report that the search succeeded. */
  }
  if (target < node->value) { /* Smaller keys belong in the left subtree. */
    return contains(node->left, target); /* Continue the search recursively on the left. */
  }
  return contains(node->right, target); /* Larger keys belong in the right subtree. */
}
