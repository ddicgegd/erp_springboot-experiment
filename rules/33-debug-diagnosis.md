# Rule: Debug Diagnosis

**File Path:** `rules/33-debug-diagnosis.md`

## Purpose

Use this rule to diagnose a failure the way a developer debugs in an IDE: identify the exact error, the exact failing location, the execution path that reaches it, and the evidence that supports the diagnosis.

This rule is source-read-only. It never changes application code, tests, configuration, schema, dependencies, generated sources, fixtures, or documentation.

The output is a diagnosis, not a fix.

## Trigger

Apply when the user's primary request is to debug, diagnose, trace, locate, explain, or find the root cause of an error.

Also apply when the user explicitly says:

- `debug`
- `diagnose`
- `chẩn đoán`
- `tìm lỗi`
- `root cause`
- `use debug skill`
- `dùng skill debug`
- `[DEBUG]`

Do not apply this rule when the user asks to implement, fix, refactor, or create a plan. Route those requests to the matching implementation or planning rule.

## 1. Source-Read-Only Lock

This lock applies for the entire rule.

Allowed actions:

- Read files.
- Search files.
- Inspect logs, stack traces, configs, schemas, tests, and command output.
- Run commands that reproduce or observe the failure without editing tracked files.
- Use build/test commands only as observation tools.
- Allow normal build outputs such as `target/` only when produced by the observation command.

Forbidden actions:

- Edit tracked files.
- Create source files.
- Delete files.
- Format code.
- Apply patches.
- Change dependencies.
- Change database schema or data.
- Add temporary tests.
- Add logging or instrumentation.
- Auto-fix the suspected cause.
- Run code-generation commands that modify the workspace.
- Commit, stash, reset, clean, rebase, or switch branches.

Completion criteria:

- Git status is checked before diagnosis commands that may execute project code.
- No tracked file content is changed by the agent.
- If a command may modify the workspace or external state, the agent asks before running it.
- If diagnosis needs instrumentation, the agent reports the needed instrumentation as a next step and stops.

## 2. Intake

Collect the smallest evidence set needed to reproduce or locate the failure.

Inspect provided context first:

- user-provided error message
- stack trace
- failing command
- request payload
- endpoint
- log fragment
- database error
- recent user-described behavior

If context is missing, inspect the code path and ask for only the missing runtime evidence that can change the diagnosis.

Completion criteria:

- The agent identifies the observed failure text, or states that no concrete failure text was provided.
- The agent identifies the entrypoint under investigation, or asks for the entrypoint if code cannot reveal it.
- The agent does not jump to a fix before locating the failure.

## 3. Reproduction And Trace

Build an evidence chain from symptom to failing location.

The trace must identify, when available:

- command, request, event, or job that starts the flow
- controller/API/consumer/listener entrypoint
- service/use-case method
- mapper/converter involved
- repository/query or external call involved
- entity/schema/config constraint involved
- exact exception class and message
- top relevant stack frame inside project code
- lower-level framework/database/client error that rejects the operation

Completion criteria:

- The diagnosis distinguishes symptom location from cause location.
- The diagnosis names the first project-code frame that leads to the failure.
- The diagnosis names the external boundary that throws or rejects the operation when present.
- If the failure cannot be reproduced, the agent reports the attempted observation and the missing evidence.

## 4. Root Cause Gate

The agent may state a root cause only when the evidence chain supports it.

A valid root cause must include:

- exact error
- exact failing file and method when available
- exact line number when available
- failing state or value
- reason that state violates code, schema, API, config, or business rules
- evidence source for each claim

If evidence supports only a hypothesis:

- label it `Hypothesis`
- state what evidence is missing
- state the next read-only observation needed
- do not present it as the final cause

Completion criteria:

- No claim relies on an unstated assumption.
- No fix is presented as already performed.
- The agent does not say "fixed", "updated", or "changed" unless the user explicitly moved to a different rule and approved edits.

## 5. Report Format

The final response must contain:

1. `Debug State`
2. `Exact Error`
3. `Failing Location`
4. `Execution Path`
5. `Root Cause`
6. `Evidence`
7. `Not Changed`
8. `Next Read-Only Check` when confidence is below high

Section requirements:

- `Debug State`: `DIAGNOSED`, `HYPOTHESIS`, or `NEEDS_RUNTIME_EVIDENCE`.
- `Exact Error`: quote the exact exception/message when available.
- `Failing Location`: file, method, and line number when available.
- `Execution Path`: ordered path from entrypoint to failure.
- `Root Cause`: one precise cause, or a clearly labeled hypothesis.
- `Evidence`: commands, logs, stack frames, code references, schema/config references.
- `Not Changed`: state that no tracked files were edited, created, deleted, or auto-fixed.
- `Next Read-Only Check`: the next observation needed before any fix planning.

## 6. Stop Conditions

Stop after the diagnosis report.

If the user asks for a fix after the report, classify the new request against the router and load the matching rule before editing.

If the user asks for a plan after the report, classify the new request against the router and load the matching planning rule.

Completion criteria:

- The debug rule ends with diagnosis only.
- The agent never transitions from diagnosis to implementation in the same rule.
