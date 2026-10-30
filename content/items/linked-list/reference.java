class LinkedList {
  static final class Node { int value; Node next; Node(int value) { this.value = value; } }
  private Node head;

  public void addFirst(int value) {
    Node node = new Node(value);
    node.next = head;
    head = node;
  }

  public void addLast(int value) {
    Node node = new Node(value);
    if (head == null) { head = node; return; }
    Node current = head;
    while (current.next != null) current = current.next;
    current.next = node;
  }

  public Integer removeFirst() {
    if (head == null) return null;
    int value = head.value;
    head = head.next;
    return value;
  }

  public String values() {
    StringBuilder out = new StringBuilder();
    for (Node current = head; current != null; current = current.next) {
      if (!out.isEmpty()) out.append(',');
      out.append(current.value);
    }
    return out.toString();
  }
}
