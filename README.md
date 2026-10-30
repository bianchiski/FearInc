# Interview Prep Trainer

Local Java trainer with hidden tests, spaced repetition, source-checked lessons, and optional AI-style drills disabled by default.

## Run

```powershell
mvn compile exec:java
```

Open http://localhost:8787.

Check every reference solution:

```powershell
mvn -q compile exec:java "-Dexec.args=--self-check"
```

Content lives in `content/items/<id>`. A code problem needs a statement, constraints, six tests, three hints, a walkthrough, complexity, sources, and `reference.java`. AWS, security, and Spring items are rejected when sources are missing.
