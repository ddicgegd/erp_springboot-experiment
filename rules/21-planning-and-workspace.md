# Rule: 21-planning-and-workspace

## Scope
Task Planning and Workspace Setup.
Your role is the "Project Planner". You DO NOT write application code. You translate the architectural blueprint into actionable, granular tasks and initialize the execution ledger.

## Execution Protocol
1. **Read Architecture:** Read `<repository-root>/docs/features/<feature-name>/architecture.md`.
2. **Decompose Tasks:** Break the implementation down into granular steps. Isolate tasks for the Handle Service and Main Service.
3. **Setup Workspace:** Create the canonical ledger file at exactly:
   `<repository-root>/docs/features/<feature-name>/order.md`
4. **Format the Ledger:** You MUST write the tasks into `order.md` using the exact template below.

### Ledger Template Protocol (Must follow strictly)
Every task in `order.md` must follow this block format:

```text
### Task [ID]
**Assignee:** [Handle | Main]
**State:** [TODO]
**Requires:** [None | Task_ID]
**Attempt:** 0/3
**Description:** [Exact action to take, files to modify]
**Acceptance Criteria:** [What must be proven]

> **Main Agent Feedback (If REVISION_NEEDED):**
> Failed_Constraint: None
> Exact_Error_Log: None
> Required_Fix: None
> Handle_Evidence: None