/* Append an undirected edge. Each node stores neighbors in insertion order. */
void add_edge(int *neighbors[], int counts[], int from, int to) {
  neighbors[from][counts[from]++] = to; /* from can now walk to to. */
  neighbors[to][counts[to]++] = from; /* to can walk back to from. */
}

/* Breadth-first order: expand a node only after earlier nodes are queued. */
int bfs(int start, int *neighbors[], int counts[], int order[]) {
  int queue[64]; /* Enough for the small graphs in this exercise. */
  int seen[64] = {0}; /* seen[node] is 1 after the node is discovered. */
  int head = 0, tail = 0, length = 0;
  queue[tail++] = start; /* The start node is the first discovery. */
  seen[start] = 1;
  while (head < tail) { /* Continue while discovered nodes remain. */
    int node = queue[head++]; /* Take the oldest discovered node. */
    order[length++] = node; /* Record it in visit order. */
    for (int i = 0; i < counts[node]; i++) { /* Walk neighbors in insertion order. */
      int next = neighbors[node][i];
      if (seen[next]) continue; /* A cycle or duplicate edge must not enqueue twice. */
      seen[next] = 1; /* Mark on discovery, before the node is expanded. */
      queue[tail++] = next;
    }
  }
  return length; /* Caller prints order[0..length). Unreached nodes are absent. */
}

/* Depth-first order: follow the first new neighbor before the later ones. */
void dfs_walk(int node, int *neighbors[], int counts[], int seen[], int order[], int *length) {
  if (seen[node]) return; /* Already on the path or finished. */
  seen[node] = 1;
  order[(*length)++] = node; /* Preorder: record the node before its neighbors. */
  for (int i = 0; i < counts[node]; i++) { /* Insertion order chooses the next branch. */
    dfs_walk(neighbors[node][i], neighbors, counts, seen, order, length);
  }
}
