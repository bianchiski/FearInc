# Learning-loop pilot

The pilot covers Foundations and Data Structures. It keeps learner data local and
uses the existing server records instead of adding analytics infrastructure.

## Success checks

Capture these measures for a small set of practice sessions:

- **Time to first practice:** time from opening the trainer to the first judge,
  answer, or review action.
- **Recommended-action starts:** the percentage of sessions whose first item is
  the item returned by `/api/catalog.next`.
- **Session completion:** the percentage of sessions that reach the `complete`
  session step after an attempt and reflection.
- **Retry after feedback:** the percentage of incorrect checks followed by a
  retry using the remediation prompt.
- **Delayed recall quality:** quality recorded when a concept appears in
  `/api/catalog.concepts` with `status: "review"`.

The first four measures can be observed from the browser interaction sequence;
the last one is available in the local concept-review database through the
catalog response. Record a baseline before changing content, then compare the
same measures after learners use the guided routine.

## Automated coverage

The test suite exercises the loop through the real HTTP server:

- next-action/session resume;
- prerequisite-aware catalog data and concept scheduling;
- targeted first-miss and retry feedback;
- API error responses and JavaScript module delivery;
- local draft save, restore, clear, and storage-failure fallback.

Run the checks with:

```powershell
mvn test
npm run test:client
mvn -q compile exec:java "-Dexec.args=--self-check"
```

AI-style drills remain excluded from the pilot unless explicitly enabled.
