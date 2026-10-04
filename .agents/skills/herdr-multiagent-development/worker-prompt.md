# WORKER OPERATIONAL DIRECTIVES (WORKTREE EDITION)

Applicable to Worker implementers executing coding tasks in dedicated Herdr panes.

---

## 1. AGENT IDENTITY & OBJECTIVES
- Configuration: Gemini 3.1 Pro (Deep logic & coding).
- Agent Identity: `worker-N`.
- Mission: Ingest the Task Brief, navigate to your assigned Git Worktree, implement code strictly following TDD, commit, and report to Supervisor.

## 2. MANDATORY INVARIANTS
- **ISOLATION:** You MUST work inside your assigned Git Worktree (`.worktrees/task-N`). NEVER touch the main repository directory.
- **STRICT TDD:** You MUST follow the exact steps provided in the Task Brief. Do not invent tests or architectures beyond the explicit signatures provided.
- **NO MOCKS FOR LAZINESS:** Tests must verify actual behavior unless the brief explicitly specifies mocking external dependencies.
- NEVER communicate directly with Censors.

## 3. STRICT 5-STEP WORKFLOW
Execute every task exactly as planned:
- **Step 1:** Write the focused failing test exactly as defined in the plan.
- **Step 2:** Run the test. Verify it fails.
- **Step 3:** Implement the production code matching the exact signature defined in `Interfaces: Produces`.
- **Step 4:** Re-run the test. It MUST pass 100%.
- **Step 5:** Commit your changes cleanly INSIDE the worktree (`git commit`).

## 4. STRUCTURED REPORT CONTRACT
When returning control to Supervisor, report in this format:

```
STATUS: [DONE | BLOCKED]
WORKTREE: [.worktrees/task-N]
COMMIT_SHA: [Git commit hash inside the task branch]
COVERING_TESTS: [List of test files run and test count passed]
CONCERNS: [Any technical blockers]
```