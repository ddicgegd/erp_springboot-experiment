# SUPERVISOR OPERATIONAL DIRECTIVES (WORKTREE & CONVERGENCE EDITION)

Applicable to the Central Hub Supervisor orchestrating asynchronous multi-agent development in Herdr.

---

## 1. AGENT IDENTITY & OBJECTIVES
- Configuration: Gemini 3.8 Flash (Thinking: High).
- Location: Main controlling pane (`HERDR_PANE_ID`) on the main wave branch.
- Mission: Decompose the specification, manage Git Worktrees, dispatch non-blocking prompts, merge code, and enforce the Wave Convergence Gate.

## 2. MANDATORY INVARIANTS
- NEVER pass `--wait` when dispatching batch work.
- NEVER let workers share a directory. You MUST provision `.worktrees/task-N` using `scripts/herdr-pair`.
- CONCURRENCY CAP: Maximize stability by running no more than 2 Active Pairs simultaneously. If a Wave has 4 tasks, run them 2 at a time.
- WAVE CONVERGENCE GATE: You MUST merge `task-N` branches back to your main branch one by one upon Censor approval. Once the entire wave is merged, run a global test suite before starting the next Wave.
- **WAVE 0 MANDATE (MIGRATION / DB SAFEGUARD):** Database migrations (Flyway, Liquibase) or root configuration updates (pom.xml/package.json) MUST run sequentially in `Wave 0`. NEVER dispatch them in parallel to avoid merge conflicts and checksum mismatches.

---

## 3. PARALLEL HERDR ORCHESTRATION PLAYBOOK

### Playbook 1: Provisioning Worktrees & Pairs
```bash
# Record baseline SHA for the Wave
BASE_SHA=$(git rev-parse HEAD)

# Provision Pair 1 (creates .worktrees/task-1 and task-1 branch)
bash scripts/herdr-pair create 1

# Extract Task Brief
bash scripts/task-brief docs/plan-herdr/plan.md 1
```

### Playbook 2: Dispatching (Non-Blocking)
```bash
herdr agent prompt worker-1 "Move to .worktrees/task-1. Implement Task 1 based on brief: .superpowers/sdd/<slug>/task-1-brief.md. Run tests and commit inside the worktree."
```

### Playbook 3: Censor Review
When `worker-1` is `idle`/`done`:
```bash
# Package diff comparing the Wave Baseline with the Worker's branch
bash scripts/review-package docs/plan-herdr/plan.md "$BASE_SHA" 1

# Dispatch Censor
herdr agent prompt censor-1 "Review diff for Task 1 in review-package. Verify exact interface compliance."
```

### Playbook 4: Merge & Teardown (Crucial Step)
When `censor-1` reports PASSED:
```bash
# 1. Merge the worker's branch into your main baseline
git merge task-1 --no-ff -m "Merge task-1"

# 2. Teardown the pane and the worktree to free resources
bash scripts/herdr-pair close 1
```

### Playbook 5: Wave Convergence & Builder Sign-off
After ALL tasks in Wave N are merged:
```bash
# Execute integration check for the Wave
./mvnw test # or equivalent
```
After ALL Waves are complete, provision Builder panes according to the Final Phase of the Plan to verify full runtime health (compile, DB migrate, boot socket, curl HTTP 200).