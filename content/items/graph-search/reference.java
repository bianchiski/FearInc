class Graph {
  private final java.util.Map<Integer, java.util.List<Integer>> edges = new java.util.LinkedHashMap<>();

  public void addEdge(int from, int to) {
    edges.computeIfAbsent(from, key -> new java.util.ArrayList<>()).add(to);
    edges.computeIfAbsent(to, key -> new java.util.ArrayList<>()).add(from);
  }

  public String bfs(int start) {
    java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
    java.util.Set<Integer> seen = new java.util.LinkedHashSet<>();
    java.util.List<Integer> order = new java.util.ArrayList<>();
    queue.add(start);
    seen.add(start);
    while (!queue.isEmpty()) {
      int node = queue.removeFirst();
      order.add(node);
      for (int next : edges.getOrDefault(node, java.util.List.of())) {
        if (seen.add(next)) queue.add(next);
      }
    }
    return join(order);
  }

  public String dfs(int start) {
    java.util.List<Integer> order = new java.util.ArrayList<>();
    walk(start, new java.util.LinkedHashSet<>(), order);
    return join(order);
  }

  private void walk(int node, java.util.Set<Integer> seen, java.util.List<Integer> order) {
    if (!seen.add(node)) return;
    order.add(node);
    for (int next : edges.getOrDefault(node, java.util.List.of())) walk(next, seen, order);
  }

  private static String join(java.util.List<Integer> order) {
    StringBuilder text = new StringBuilder();
    for (int node : order) {
      if (text.length() > 0) text.append(' ');
      text.append(node);
    }
    return text.toString();
  }
}
