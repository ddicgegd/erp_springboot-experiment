# Codex Project Instructions

This file is mandatory. Codex must read and follow this file before starting any task in this repository.

## Rule Loading Protocol

Before doing any task:
1. Classify the user's request.
2. Read the specific rule file whose trigger matches the request.
3. If multiple triggers match, stop and ask the user to clarify the primary objective.
4. Do not edit files until the applicable rule file has been read.
5. If a referenced rule file is missing, stop and report it before continuing.

## Primary Workflow Rules (MUTUALLY EXCLUSIVE)
**CRITICAL:** You MUST select ONLY ONE rule per conversational turn. You are STRICTLY FORBIDDEN from reading, mentioning, or anticipating any rule file that has not been explicitly triggered.

**Read `rules/20-architecture-design.md`**
- Trigger: The user requests high-level architecture, system design, or invokes `/grill-me`.
- Condition: The feature involves module boundaries, contracts, data ownership, or async communication.
- Exclusions: Do not apply to localized bug fixes, mechanical refactors, or CRUD tasks.

**Read `rules/21-handle-service-execution.md`**
- Trigger: The user explicitly provides the exact command: `[EXECUTE_HANDLE_SERVICE]`.
- Condition: Do not read, suggest, or anticipate this rule under any other circumstances. 

**Read `rules/22-main-service-report-control.md`**
- Trigger: The user explicitly provides the exact command: `[EXECUTE_MAIN_SERVICE_CONTROL]`.
- Condition: Do not read, suggest, or anticipate this rule under any other circumstances.

**Read `rules/31-planning-mode.md`**
- Trigger: The user explicitly types the exact command: `/plan`.
- Condition: Do not read this rule automatically. Do not trigger based on generic words like "plan", "thiết kế", or "kế hoạch".

**Read `rules/32-execution-mode.md`**
- Trigger: The user explicitly types the exact command: `/execute-plan`.
- Condition: Do not read this rule automatically. 

**Read `rules/30-backend-spring.md`**
- Trigger: The user specifically requests Java/Spring Boot backend implementation within an active execution mode.