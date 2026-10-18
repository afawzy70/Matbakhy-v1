QA execution is blocked: the command runner fails before starting any command:

```text
bwrap: setting up uid map: Permission denied
```

This also occurs with a non-login shell. I could not read the QA role or repository guidance, resolve `HEAD`, run suites, create reports, or commit.

Counts: **0 tests executed; all requested suites NOT_RUN**. Defects by severity: **unknown—no product assessment was possible**. No files or branches were changed.

The runner’s sandbox initialization needs repair before I can complete the work in the specified worktree.