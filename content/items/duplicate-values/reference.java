class Solution {
  public boolean hasDuplicate(int[] values) {
    java.util.Set<Integer> seen = new java.util.HashSet<>();
    for (int value : values) if (!seen.add(value)) return true;
    return false;
  }
}
