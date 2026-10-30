class OpenMap {
  static final class Node { String key; int value; Node next; }
  private Node[] buckets = new Node[4];
  private int size;

  public void put(String key, int value) {
    int index = bucket(key, buckets.length);
    for (Node node = buckets[index]; node != null; node = node.next) {
      if (node.key.equals(key)) { node.value = value; return; }
    }
    Node created = new Node();
    created.key = key;
    created.value = value;
    created.next = buckets[index];
    buckets[index] = created;
    size++;
    if (size > buckets.length) resize();
  }

  public int get(String key) {
    for (Node node = buckets[bucket(key, buckets.length)]; node != null; node = node.next) {
      if (node.key.equals(key)) return node.value;
    }
    throw new java.util.NoSuchElementException(key);
  }

  public boolean contains(String key) {
    for (Node node = buckets[bucket(key, buckets.length)]; node != null; node = node.next) {
      if (node.key.equals(key)) return true;
    }
    return false;
  }

  public int size() { return size; }

  private void resize() {
    Node[] next = new Node[buckets.length * 2];
    for (Node bucket : buckets) {
      for (Node node = bucket; node != null; node = node.next) {
        int index = bucket(node.key, next.length);
        Node copy = new Node();
        copy.key = node.key;
        copy.value = node.value;
        copy.next = next[index];
        next[index] = copy;
      }
    }
    buckets = next;
  }

  private static int bucket(String key, int length) {
    return Math.floorMod(key.hashCode(), length);
  }
}
