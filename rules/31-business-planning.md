# Rule: Business Planning

**File Path:** `rules/31-business-planning.md`

## Purpose

Use this rule to create a business-grade implementation plan for work that touches business behavior, service/domain logic, persistence, API contracts, side effects, or cross-module behavior.

This rule produces a plan only. It does not implement, commit, stash, reset, create files for execution, or switch branches while the plan is still draft.

A plan remains draft until the user confirms the final plan with `/goal`.

## Trigger

Apply when the request touches any of these:

- business workflow
- service/domain behavior
- entity/model/DTO mapping
- persistence/schema/migration
- API contract
- auth/user/customer context
- event/outbox/Kafka/webhook/payment/inventory
- cross-module behavior
- non-trivial bug fixing
- planned refactor with behavioral risk

Also apply when the user explicitly says:

- `use rule 31`
- `dùng rule 31`
- `[RULE_31]`
- `/grill-me`
- `/to-spec`
- `/to-tickets`
- `/goal`

## 1. Branch Selection Gate

This gate runs first.

Before reading code for the plan, check:

1. Current git branch.
2. Git status summary.
3. Whether the current branch name matches the requested work.

Then ask the user to choose exactly one branch direction:

1. Continue planning for the current branch.
2. Use a new branch, with one proposed branch name derived from the task.

Completion criteria:

- The branch question is the first decision requested from the user.
- The agent records the selected branch direction in the plan.
- The agent does not switch, create, rename, stash, commit, reset, clean, rebase, or revert during draft planning.
- If the user confirms the final plan with `/goal`, execution must start by moving to the selected branch direction before implementation.
- If the user has already provided an explicit branch decision in the same request, record it and continue.

If the current branch is `main` or `master`, the agent must recommend a new branch name and wait for the user's branch decision before planning.

## 2. Read-First Context Gate

After the branch direction is known, perform read-only exploration before producing or updating the plan.

Inspect every relevant artifact type below that can affect the requested behavior:

- controller/API entrypoint
- service/use-case owner
- entity/model/value object
- DTO/request/response
- mapper/converter
- repository/query/specification
- migration/schema/database constraint
- transaction boundary
- event/outbox/Kafka/webhook/payment/inventory/email/cache/external call
- auth/user/customer context
- existing test or verification command
- error log, failing command, or runtime evidence when fixing a bug
- module boundary and ownership

Completion criteria:

- Every artifact type that can change the plan is either inspected or marked `Not involved`.
- Persistence work includes entity-schema-DTO alignment.
- Side-effect work includes producer, consumer, retry, idempotency, and failure behavior when present.
- The agent does not edit files during this rule.

## 3. Evidence Gate

Decide whether the available evidence is sufficient to produce or update the plan.

Evidence is sufficient only when the agent can state these elements without inventing facts that may change implementation:

1. Actor: who starts or owns the workflow.
2. Trigger: when the workflow starts.
3. Input: what data enters and from where.
4. Business rules: validation, allowed states, failure rules.
5. State/persistence: what is saved, updated, snapshotted, related, or forbidden.
6. Side effects: events, outbox, Kafka, webhook, payment, inventory, email, cache, external calls.
7. Verification: command, existing test, log, API call, DB query, benchmark, or manual proof.

If evidence is sufficient:

- Produce or update the draft plan in the same response.
- Mark inferred items as `Assumption`.
- Prefer behavior already proven by code, schema, tests, logs, or the user's request.

If evidence is insufficient:

- Ask only questions whose answers can change implementation.
- Ask at most 3 questions per round.
- Each question must cite the code evidence, missing fact, or conflict that makes the answer necessary.
- Each question must include the default assumption the agent will use if the user says to proceed with defaults.
- Stop after asking; do not produce or update the plan until the user answers or accepts defaults.

Completion criteria:

- No question is asked only to satisfy process.
- No unresolved assumption is hidden inside a milestone.
- A missing fact that can change behavior blocks the plan.

## 4. Business Contract

The plan must start from a Business Contract.

The Business Contract must include:

- Actor
- Trigger
- Input
- Business rules
- State/persistence
- Side effects
- Non-goals
- Invariants
- Failure cases
- Verification
- Open assumptions

Completion criteria:

- Every milestone in the plan maps to at least one Business Contract item.
- Every Business Contract item is either implemented by a milestone, explicitly out of scope, or listed as an open assumption.
- The contract does not contain generic placeholders.

## 5. Business-Grade Plan

Create the plan that gives the best implementation outcome for the business behavior, based on the codebase evidence and the Business Contract.

Each milestone must include:

- Objective
- Business Contract items satisfied
- Files to read or edit
- Exact implementation logic
- State and persistence impact
- Side effects and failure handling
- Forbidden changes
- Verification method

The plan must account for each component below when relevant:

- Boundary: controller/API entrypoint, service/use-case owner, module ownership.
- Contract: request/response DTOs, mapper behavior, status codes, error semantics.
- Domain state: entity/value object changes, invariants, allowed transitions.
- Persistence: repository queries, schema constraints, migrations, transaction boundaries.
- Side effects: events, outbox, Kafka, webhook, payment, inventory, email, cache, external calls.
- Concurrency/idempotency: duplicate requests, retries, stale reads, locking, optimistic updates.
- Observability: logs, metrics, audit records, traceable proof.
- Rollback: unchanged behavior, partial failure recovery, data repair if needed.
- Verification: the smallest proof set that demonstrates the contract is satisfied.

Completion criteria:

- No vague milestone remains.
- No planned edit exists without a Business Contract reason.
- No persistence change exists without schema/entity/DTO alignment.
- No mapper or DTO change exists without request/response impact.
- No side effect is treated as incidental.
- No test-only proof is accepted when runtime, schema, or integration proof is required.

## 6. Verification And Test Evidence

Verification is proof that the plan satisfies the Business Contract.

Tests are required only when the business behavior needs them as evidence.

Rules:

- Prefer existing tests, focused commands, logs, API calls, DB queries, or manual proof when they prove the contract with less churn.
- Add permanent tests only when the change alters reusable business behavior, public contracts, persistence rules, or regression-prone logic.
- Add temporary exploratory tests only when they answer a specific uncertainty.
- Temporary exploratory tests must be deleted after they pass or fail and the result is captured in the report.
- Deleted temporary tests are proof logs, not retained coverage.

Completion criteria:

- Every verification item names the proof method.
- Every temporary test has a delete condition.
- Every needed permanent test has a business reason.
- The final report must state which tests were run, which temporary tests were deleted, and what evidence remains.

## 7. Context Cleanup

Every draft plan must include a `Context Cleanup` section.

The section must classify context into:

- Keep: facts, code paths, constraints, and decisions required for execution.
- Drop: exploration notes, rejected assumptions, irrelevant files, stale hypotheses, and duplicate context.
- Re-check: facts that must be verified again during execution because they may change.

Completion criteria:

- The execution context is smaller than the exploration context.
- The plan can be resumed from `Keep` and `Re-check` without rereading irrelevant material.
- Rejected assumptions are not carried forward as open assumptions.
- If the user adds or changes context after a draft plan, update `Context Cleanup` before updating milestones.

## 8. Draft And Final Plan State

The first plan produced by this rule is `PLAN DRAFT`.

The user may add context, correct assumptions, or request changes after any draft plan. The agent must then update the Business Contract, Business-Grade Plan, Verification, and Context Cleanup sections affected by the new context.

The plan becomes final only when the user confirms with `/goal`.

When `/goal` is received:

- Mark the plan as `PLAN LOCKED`.
- Restate the selected branch direction.
- State that execution must start by moving to the selected branch direction.
- Do not add new business assumptions.
- Do not add new milestones unless the user includes new context with `/goal`; if new context appears, keep the plan as `PLAN DRAFT`.

Completion criteria:

- Draft plans remain editable.
- Locked plans contain no unresolved business blocker.
- Branch direction is present in both draft and locked plans.
- The rule does not produce a separate prompt or usage instruction.

## 9. Output Format

When asking for branch direction, output only:

1. Current branch and status summary.
2. Recommended branch direction with proposed new branch name.
3. The branch question.

When producing or updating a draft plan, output:

1. `Plan State: PLAN DRAFT`
2. `Selected Branch Direction`
3. `Business Contract`
4. `Business-Grade Plan`
5. `Verification And Test Evidence`
6. `Context Cleanup`
7. `Open Decisions`

When locking the plan after `/goal`, output:

1. `Plan State: PLAN LOCKED`
2. `Selected Branch Direction`
3. `Business Contract`
4. `Business-Grade Plan`
5. `Verification And Test Evidence`
6. `Context Cleanup`
7. `Execution Start Requirement`

The output must be specific to the discovered code and business behavior. It must not contain generic filler, illustrative examples, or instructions for using another workflow.
