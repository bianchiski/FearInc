# Interview Prep Trainer

Local Java trainer with hidden tests, spaced repetition, source-checked lessons, and optional AI-style drills disabled by default.

The code judge runs locally with a memory limit and time limit. It is not a security
sandbox; only run code you trust on your machine.

## Run

```powershell
mvn compile exec:java
```

Open http://localhost:8787. The server listens on 127.0.0.1 only.

`mvn test` checks every reference solution and checks that the C solution stays hidden until a problem is solved. `mvn -q compile exec:java "-Dexec.args=--self-check"` still runs the reference check on its own.

Content lives in `content/items/<id>`. `catalog.json` is the syllabus and lists every
item exactly once. A code problem needs a statement, constraints, six tests, three
hints, a walkthrough, complexity, sources, `className`, and `reference.java`.
AWS, security, and Spring items are rejected when sources are missing. The C solution
is sent only after the hidden tests pass, or when that item is already mastered.

## Desktop package

Build a self-contained Windows app image with:

```powershell
powershell -ExecutionPolicy Bypass -File .\package-fearinc.ps1
```

The executable is created at
`target\FearInc-package\FearInc\FearInc.exe`. It stores packaged-user data under
`%APPDATA%\FearInc` and opens the trainer in the browser automatically.

The guided learning-loop pilot and its success checks are documented in
`docs/learning-loop-pilot.md`. Run `mvn test` for the server and HTTP learning-loop
tests, then `npm run test:client` for local draft persistence tests.
