# Rule: Phase 1 - Recognition, Plan Alignment & Skill Mapping
**File Path:** `rules/31-planning-mode.md`

## Pre-Planning Exploration (Read-Only)
Allow read-only codebase exploration (search files, read schemas, check API contracts) *before* deciding to trigger Phase 1, to accurately assess task complexity.

## Trigger

Apply ONLY when:
* The user explicitly requests plan mode, asks to create a plan, asks for analysis before implementation, or explicitly says "plan mode".

Do NOT trigger for:
* Tasks where the user hasn't explicitly invoked "plan mode", regardless of how large, risky, or cross-module the task appears.
* Simple questions, status checks, direct small code edits, or simple command requests.

*Exceptions:* 
* If the user explicitly asks to plan and execute in the same turn, provide the plan first, then continue into execution without State Lock 1.
* Bypass State Lock 1 entirely if the prompt contains automation flags (e.g., `non-interactive`, `--auto-approve`) or is triggered by a subagent/orchestrator.

## 1. Conditional Socratic Interview & Assumptions

* **Action:** Deconstruct the natural language request.
* **Questioning Rule:** Ask at most 2-3 concise questions **ONLY** when missing information would materially change the plan or create implementation risk.
* **Assumptions:** If reasonable assumptions can be made based on the codebase context, state them clearly and proceed without asking.
* **Simulation:** Apply a domain-modeling mindset to outline the architectural approach or workflow.

## 2. Contextual Skill Mapping

Do not forcefully scan the filesystem for skills unless necessary.

* **Skill Discovery:** Use the currently available system skill list surfaced in the session context first. Only inspect `.agents/skills/` when repo-local skills are highly relevant and not already surfaced in the session.
* **Fallback:** If no applicable skill exists, write `Applicable Skill: None`. Do not invent skill names.
* **Routing:** Pinpoint exactly which Milestone requires which skill to ensure smooth execution in Phase 2.

## 3. Scalable Plan Generation

Generate the Plan based on task complexity to optimize the context window.

Do not output placeholder text or this template verbatim. Replace every field with task-specific content.

* **Small Tasks:** Output a compact, streamlined plan.
* **Medium Tasks (Nested Checklist):** Do not split into two plans. Embed the Control Harness constraints directly inside each Milestone.
* **Large/Risky Tasks:** Output the full, detailed dual-plan. **Never** merge Plan 1 and Plan 2 for these tasks.

### PLAN 1: BUSINESS EXECUTION PLAN (For Large Tasks)

Break down the task into explicit, sequential milestones. Document applicable skills as metadata rather than execution commands.

For each milestone include:

* **Milestone:** Technical or business objective.
* **Execution Logic:** Task-specific implementation steps.
* **Applicable Skill:** skill name or `None`.
* **Skill Status:** `Loaded`, `Available but not loaded`, or `None`.
* **Applied at milestone:** Milestone name.

### PLAN 2: CONTROL HARNESS (For Large Tasks)

1. **Outcome:** Quantitative or qualitative technical target.
2. **Verifiable End State:** Completion condition backed by verification methods.
3. **Relevant Context:** Exact paths to required source code.
4. **Constraints:** Forbidden zones and invariants.
5. **Validation Loop:** CLI test commands or manual verification steps.
6. **Workspace & Logging:**
    * *Phase 1 Rule:* Do not create, edit, delete, move, or format files during Phase 1 unless the user explicitly requests saving planning artifacts.
    * *Execution Rule:* Defer all progress logging to the execution phases if the task spans multiple steps. All log files (e.g., `PROGRESS.md`) **MUST** be strictly contained within the `.agents/` directory.
    * *Git Cleanliness:* Before writing to `.agents/PROGRESS.md`, verify `.agents/` is in `.gitignore`. If missing, append it first.
7. **Stop Rules:** Stop when the scoped implementation is complete and the agreed verification passes. If tests are unavailable, failing for unrelated pre-existing reasons, or impossible to run, report that clearly with evidence.

## 4. State Lock 1 (Yield Turn - HARD STOP)

* **Action:** You MUST STOP GENERATING TEXT AND YIELD THE TURN immediately after outputting the Lock Syntax.
* **CRITICAL ANTI-HALLUCINATION RULE:** 
  - Do NOT simulate or hallucinate the user's response.
  - Do NOT proceed to Phase 2 or execute any plan steps in this same response.
  - Do NOT invoke any file-writing, code-editing, or terminal-execution tools in Phase 1.
* **Lock Syntax:** Output the exact ASCII string below at the very end of your response, and then IMMEDIATELY STOP GENERATING:

`[STATE LOCK 1] Phase 1 complete. Awaiting confirmation: YES / Adjustments.`
`***AGENT DIRECTIVE***: If the user replies YES, your VERY NEXT ACTION must be to read rules/32-execution-mode.md. DO NOT write any implementation code until this file is read.`