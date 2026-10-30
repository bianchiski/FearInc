# Module content format

Each module may be expanded into its own folder with a `module.yaml` and Markdown lessons:

```yaml
id: data-structures
title: Data Structures from Scratch
track: Core
items:
  - type: lesson
    id: linked-list-nodes
  - type: code-problem
    id: implement-linked-list
  - type: quiz
    id: node-invariants
```

The server currently loads the catalog index and stores attempts/progress in SQLite. This format keeps content independent from application code.
