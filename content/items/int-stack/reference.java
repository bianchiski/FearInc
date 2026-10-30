class IntStack {
  private int[] data = new int[2];
  private int size;
  public void push(int value) {
    if (size == data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
    data[size++] = value;
  }
  public int pop() {
    if (size == 0) throw new IllegalStateException("empty");
    return data[--size];
  }
  public int size() { return size; }
}
