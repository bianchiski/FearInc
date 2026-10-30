# Interview Prep Trainer

Local Java trainer with hidden tests, spaced repetition, source-checked lessons, and optional AI-style drills disabled by default.

## Run

```powershell
mvn compile exec:java
```

Open http://localhost:8787. The server listens on 127.0.0.1 only.

`mvn test` checks every reference solution and checks that the C solution stays hidden until a problem is solved. `mvn -q compile exec:java "-Dexec.args=--self-check"` still runs the reference check on its own.

Content lives in `content/items/<id>`. `catalog.json` lists every item id, and a `module.json` problem id must match an item in that module. A code problem needs a statement, constraints, six tests, three hints, a walkthrough, complexity, sources, `className`, and `reference.java`. AWS, security, and Spring items are rejected when sources are missing. The C solution is sent only after the hidden tests pass, or when that item is already mastered.
