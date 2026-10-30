class Bst {
  static final class Node { int value; Node left, right; Node(int value) { this.value = value; } }
  private Node root;

  public void insert(int value) { root = insert(root, value); }
  public boolean contains(int value) { return contains(root, value); }
  public String inorder() { StringBuilder out = new StringBuilder(); inorder(root, out); return out.toString(); }

  private Node insert(Node node, int value) {
    if (node == null) return new Node(value);
    if (value < node.value) node.left = insert(node.left, value);
    else if (value > node.value) node.right = insert(node.right, value);
    return node;
  }

  private boolean contains(Node node, int value) {
    if (node == null) return false;
    if (value == node.value) return true;
    return value < node.value ? contains(node.left, value) : contains(node.right, value);
  }

  private void inorder(Node node, StringBuilder out) {
    if (node == null) return;
    inorder(node.left, out);
    if (!out.isEmpty()) out.append(' ');
    out.append(node.value);
    inorder(node.right, out);
  }
}
