# Rule: Phase 2 & 3 - Goal-Driven Execution & Git Routing
**File Path:** `rules/32-execution-mode.md`

## Trigger

If no approved Phase 1 Dual-Plan exists and no bypass flag is present, do not apply this rule.

Apply when:
* The user confirms Phase 1 with "YES", "proceed", "execute", or equivalent.
* The user explicitly asks to plan and execute in the same turn.
* Automation bypass flags explicitly skip Phase 1.

## 1. Environment Safety Check

Before editing files, check:
1. Current Git branch.
2. Current Git status.
3. Whether `.agents/` is ignored if progress logging is required.

Rules:
* If the current branch is `main` or `master`, stop and report before editing.
* Do not create branches, switch branches, stash, commit, rebase, reset, clean, or revert unless explicitly requested.
* Do not overwrite or revert unrelated user changes. If existing changes overlap with the target files, inspect them and work with them.
* If `.agents/` is not ignored and progress logging is required, report it. Add `.agents/` to `.gitignore` only if the execution scope allows workspace metadata edits.

## 2. Execution Harness

Use the approved Phase 1 Dual-Plan as the execution contract.

If Codex goal mode is available and explicitly allowed, convert the approved plan into a goal-driven execution objective. Otherwise, execute the milestones directly while preserving the same control harness.

Do not re-plan unless:
* The implementation contradicts the approved assumptions.
* Required files or dependencies are missing.
* A constraint cannot be satisfied.
* The same error occurs 3 times.

## 3. Milestone Execution

For each milestone:
* Read the relevant context files first.
* Apply mapped system/session skills only when available and relevant.
* Keep edits scoped to the approved milestone.
* Update progress logging only if approved and appropriate for a multi-step task.
* Run the agreed quick validation when it is practical.
* Use Codex's file editing mechanism for source changes. Do not use shell write tricks for manual edits.

## 4. Validation Loop

* Run quick checks after meaningful milestone changes when available (e.g., avoiding expensive full-suite runs after every small edit).
* Run the full agreed verification before the final response.
* If tests are unavailable, impossible to run, or failing for unrelated pre-existing reasons, report evidence clearly.
* Do not claim completion without verification evidence or an explicit reason why verification could not run.

## 5. Pause Rules

Stop and report before continuing when:
* Current branch is `main` or `master`.
* Same error occurs 3 times in a row.
* Missing credentials, secrets, services, or dependencies block progress.
* A constraint or non-goal would be violated.
* Required implementation requires touching files outside the approved scope.
* User approval is needed for Git operations or workspace metadata changes.

## 6. Stop Rules

Stop when:
* All approved milestones are implemented.
* The agreed validation passes, or unresolved verification limits are documented.
* The final response includes changed files, verification results, and any residual risks.