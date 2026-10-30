# Linked lists: a chain of boxes

## Simple version

Imagine a treasure hunt. Each box contains a value and a note telling you where the next box is.
That is a linked list. The list keeps one special reference called `head`, which points to the first box.

```text
head -> [1 | next] -> [2 | next] -> [3 | null]
```

To add at the front, make a new box and point it at the old head. Then make the new box the head.
To remove at the front, move head to the next box. The old first box is no longer part of the list.

## Small Java example

```java
Node first = new Node(1);
first.next = new Node(2);
// first -> 1 -> 2
```

## Interview version

The important invariant is that `head` always points to the first node, or is `null` for an empty list.
Adding or removing at the head takes O(1) time. Finding the end or a particular value takes O(n), because
we may need to follow every link. The nodes use O(n) extra space.

## Common traps

- Forgetting the empty-list case (`head == null`).
- Losing the old head before connecting it to the new node.
- Walking past the last node instead of stopping when `next == null`.

### Tiny glossary

- **Node:** one box containing a value and a link.
- **Reference:** a variable that points to an object.
- **Invariant:** a rule that must stay true after every operation.

When this feels comfortable, try the exercise below.
