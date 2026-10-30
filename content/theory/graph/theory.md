# Graph search

## Simple version

A graph is a set of nodes with edges between them. An undirected edge means you can walk either way. Search means listing the nodes you can reach, without stepping on the same node twice.

## How to picture it

Draw four dots. Connect 0 to 1, 0 to 2, and 1 to 3. Breadth-first search walks layer by layer: 0, then its neighbors 1 and 2, then 3. Depth-first search follows one path as far as it can: 0, then 1, then 3, then backtracks to 2.

## Interview version

Store each node's neighbors in the order the edges were added. Breadth-first search uses a queue. Depth-first search follows the first unused neighbor before the later ones. Mark a node when you first discover it, so a cycle cannot send you around forever. Time and extra space are both proportional to the nodes plus the edges you walk.

## Common traps

- Forgetting the visited set, then looping on a cycle.
- Adding an undirected edge in only one direction.
- Visiting neighbors in a different order than the order they were added.

### Tiny glossary

- **Neighbor:** a node connected by one edge.
- **Queue:** first in, first out. This gives breadth-first order.
- **Visited set:** nodes already discovered.
- **Cycle:** a path that returns to a node already on it.

Read this only as much as you need, then try the exercise.
