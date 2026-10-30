class Solution {
  public int[] pairIndices(int[] values, int target) {
    java.util.Map<Integer, Integer> seen = new java.util.HashMap<>();
    for (int i = 0; i < values.length; i++) {
      int need = target - values[i];
      if (seen.containsKey(need)) return new int[]{seen.get(need), i};
      seen.put(values[i], i);
    }
    return new int[]{-1, -1};
  }
}
