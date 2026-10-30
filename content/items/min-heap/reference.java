class MinHeap {
  private int[] data = new int[8];
  private int size;

  public void push(int value) {
    if (size == data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
    data[size] = value;
    int index = size++;
    while (index > 0) {
      int parent = (index - 1) / 2;
      if (data[parent] <= data[index]) break;
      swap(parent, index);
      index = parent;
    }
  }

  public int pop() {
    if (size == 0) throw new IllegalStateException("empty");
    int value = data[0];
    data[0] = data[--size];
    int index = 0;
    while (true) {
      int left = index * 2 + 1;
      int right = left + 1;
      int smallest = index;
      if (left < size && data[left] < data[smallest]) smallest = left;
      if (right < size && data[right] < data[smallest]) smallest = right;
      if (smallest == index) break;
      swap(index, smallest);
      index = smallest;
    }
    return value;
  }

  public int size() { return size; }

  private void swap(int left, int right) {
    int value = data[left];
    data[left] = data[right];
    data[right] = value;
  }
}
