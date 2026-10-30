class DynamicArray {
  private int[] data = new int[2];
  private int size;

  public void add(int value) {
    if (size == data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
    data[size++] = value;
  }

  public int get(int index) {
    if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
    return data[index];
  }

  public int size() { return size; }
}
