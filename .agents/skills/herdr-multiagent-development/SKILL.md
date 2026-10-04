---
name: herdr-multiagent-development
description: Plan and orchestrate parallel multi-agent development on Herdr using Git Worktree Isolation, Elastic Pairing, and the Supervisor hub.
---

# Herdr Multi-Agent Development (HMAD) - Re-Architected Edition

Supreme engineering directive: Transform specifications into robust Parallel Task Waves and orchestrate multi-agent execution on Herdr. This architecture enforces Git Worktree Isolation, Elastic Pairing (Batching vs 1-1), Cross-Verification, and a mandatory Builder integration phase.

---

## ARTICLE 1: CORE DIRECTIVES & ISOLATION INVARIANTS

### 1.1. GIT WORKTREE ISOLATION (MANDATORY)
- **Zero-Conflict Rule:** Concurrent workers MUST NEVER operate in the same physical directory.
- Every task is executed in a dedicated, isolated git worktree (`.worktrees/task-N`) backed by a dedicated branch (`task-N`).
- Workers commit strictly to their worktree branch. The Supervisor merges these branches back to the baseline after Censor sign-off.

### 1.2. ELASTIC PAIRING & SCOPING
Tasks are categorized by Scope to optimize Herdr pane layout and resource usage:
- **Scope: Small** (< 30 LOC, enums, configs): Supervisor MAY assign multiple small tasks to a single `worker-batch` sequentially. Censor verification is done once for the batch, or via Fast-Track (Supervisor self-verification based on strict TDD pass).
- **Scope: Standard** (Business logic, new services): Strictly requires the 1-1 dedicated pair (`worker-N` and `censor-N`).

### 1.3. SUPERVISOR NON-BLOCKING HUB
- ALL messages, diff transfers, and reports route through the Supervisor.
- Supervisor MUST NOT use `--wait` for batch dispatch.
- Layout Management: Supervisor MUST enforce a concurrency cap (max 2 active pairs at once per Wave) to prevent Herdr terminal pane collapse.

---

## ARTICLE 2: ROLE & AUTHORITY MATRIX

### 2.1. SUPERVISOR (CENTRAL HUB)
- Evaluates Scopes to decide Batching vs 1-1 Pairing.
- Provisions isolated Worktrees via `scripts/herdr-pair create N`.
- Merges `task-N` branches into the main baseline upon Censor approval, then triggers `scripts/herdr-pair close N`.
- Enforces the **Wave Convergence Gate**: runs a full integration test on the main baseline after all tasks in a Wave are merged, before starting the next Wave.

### 2.2. WORKER-N (INDEPENDENT IMPLEMENTER)
- Operates EXCLUSIVELY inside `.worktrees/task-N`.
- Adheres strictly to the TDD Steps defined in the plan (Signature -> Failing Test -> Minimal Code -> Pass).

### 2.3. CENSOR-N (DEDICATED REVIEWER)
- Reviews the Git diff comparing the Wave Baseline against the Worker's `task-N` branch.
- Verifies code against the exact interfaces and TDD specs defined in the plan.

### 2.4. BUILDER (RUNTIME SIGN-OFF)
- Activated after all Waves complete.
- Verifies clean compilation, DB migrations, runtime boot, and HTTP 200 health checks.

---

## ARTICLE 3: PLAN GENERATION & ARCHITECTURAL RIGOR

The HMAD skill dictates the *topology* of execution (Waves, Worktrees). However, the **CONTENT** of the plan MUST inherit deep engineering rigor:

1. **NO PLACEHOLDERS:** NEVER use lazy placeholders like `[Exact test code]`, `// handle edge cases`, or `Implement logic per spec`. 
2. **REAL SIGNATURES:** You MUST write the exact, compilable class names, function signatures, and JSON payloads.

### 3.1. WAVE PLAN STRUCTURE (WAVE 0: GLOBAL STATE)
Organized strictly into concurrent Task Waves, prioritizing a Sequential Wave 0 for global state:

```markdown
# [Feature Name] Multi-Agent Implementation Plan

## Wave 0: Global State & Foundation (Sequential)
(Strictly ONE pair only. MUST handle all Database Migrations, Flyway/Liquibase scripts, pom.xml/gradle changes, and global Security configs. This eliminates version collisions like Flyway V1__ vs V2__).

### Task 0: Database Schema & Dependencies
...
```

After Wave 0 completes, subsequent Waves (Wave 1, Wave 2) can be fully parallelized across multiple isolated task worktrees.

---

## ARTICLE 4: PLANNING STANDARD (MULTI-SERVICE & STRICT TDD)

**STACK AGNOSTIC MANDATE:** This architecture orchestrates multiple distinct services (e.g., Java/Spring backend, TypeScript/React frontend, Python workers). You MUST dynamically adapt the exact language, test framework (JUnit, Jest, PyTest, Go Test), and build commands (mvn, npm, pip) to match the target service.

Completed plans MUST be saved to: `docs/plan-herdr/YYYY-MM-DD-<feature-name>.md`.
The plan MUST strictly follow this structure, filled with **REAL CODE** matching the service's stack:

```markdown
# [Feature Name] Multi-Agent Implementation Plan

## Wave 1: [Wave Name]
**Review Focus & Convergence:** 
- [e.g., Backend: Ensure DB UniqueConstraintViolation is handled.]
- [e.g., Frontend: Verify UI gracefully displays HTTP 409 Conflict state.]

### Task 1: [Service A - e.g., Backend API Endpoint]
- Pair: worker-1 <-> censor-1
- Scope: [Small | Standard]
- Target Service: `backend/`
- Files: 
  - Create: `backend/src/.../UserHandler.java` (or .ts, .go, .py)
  - Modify: `backend/src/.../routes.java:10-30`
- Interfaces: 
  - Consumes: `UserRepository`
  - Produces: `POST /api/users -> HTTP 201 | 409`

- [ ] **Step 1: Write failing test**
\`\`\`[language]
// ACTUAL TEST CODE IN THE TARGET LANGUAGE (JUnit, Jest, PyTest, etc.)
// Example (TS/Jest): 
test('POST /api/users returns 409 on duplicate', async () => { ... });
\`\`\`
- [ ] **Step 2: Verify test fails**
Run: `[Exact test command for the service, e.g., npm run test:unit]`
Expected: FAIL
- [ ] **Step 3: Implement exact signature**
\`\`\`[language]
// EXACT SIGNATURE/CLASS/COMPONENT TO BE IMPLEMENTED
export const createUser = async (req: Request, res: Response) => { ... }
\`\`\`
- [ ] **Step 4: Verify test passes**
- [ ] **Step 5: Commit**
Run: `git commit -m "..."`

---

## Final Phase: Multi-Service Builder Sign-Off
(Define one block for EVERY service touched in this plan)

### Service: Backend (e.g., Spring/Node/Go)
- Root Directory: `backend/`
- Build Command: `./mvnw clean package` OR `npm run build`
- Migration Check: Verify schema/migrations cleanly apply
- Boot Command: `java -jar app.jar` OR `npm start`
- Health Check: `curl -f http://localhost:8080/health` -> HTTP 200 OK

### Service: Frontend (e.g., React/Vue)
- Root Directory: `frontend/`
- Build Command: `npm run build`
- Health Check: `npm run preview` -> Ensure bundle serves without errors
```

## ARTICLE 5: ORCHESTRATION & CONVERGENCE

### 5.1. WORKTREE LIFECYCLE
```bash
# Supervisor provisions isolated environment for Task 1:
bash scripts/herdr-pair create 1
# Supervisor dispatches worker:
herdr agent prompt worker-1 "Work in .worktrees/task-1. Implement Brief: <path>"
```

### 5.2. THE WAVE CONVERGENCE GATE
When `censor-N` approves:
1. Supervisor merges: `git merge task-N --no-ff` (from the main wave baseline).
2. Supervisor tears down worktree: `bash scripts/herdr-pair close 1`.
3. Once ALL tasks in Wave are merged, Supervisor MUST run the Wave Integration Test (e.g., `./mvnw test`) to catch cross-task interface breakages before starting Wave N+1.
