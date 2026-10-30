class IntQueue {
  private int[] data = new int[2];
  private int head;
  private int size;

  public void enqueue(int value) {
    if (size == data.length) grow();
    data[(head + size) % data.length] = value;
    size++;
  }

  public int dequeue() {
    if (size == 0) throw new IllegalStateException("empty");
    int value = data[head];
    head = (head + 1) % data.length;
    size--;
    return value;
  }

  public int size() { return size; }

  private void grow() {
    int[] next = new int[data.length * 2];
    for (int i = 0; i < size; i++) next[i] = data[(head + i) % data.length];
    data = next;
    head = 0;
  }
}
