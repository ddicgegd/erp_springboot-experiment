# Rule: 20-architecture-design

**File Path:** `rules/20-architecture-design.md`

## Scope

Architecture Design.

This rule produces an implementation-ready architectural blueprint. It does not implement the feature, modify application code, create migrations, or decompose work into ticket-level tasks.

Allowed actions:
- Perform read-only repository inspection.
- Analyze requirements and existing architecture.
- Ask decision-critical clarification questions.
- Define architecture, contracts, constraints, risks, and validation criteria.
- Persist the final blueprint strictly to the codebase.

## Trigger

Apply when:
- The user requests high-level architecture or system design.
- A proposed feature changes service/module boundaries, public contracts, data ownership, persistence, asynchronous communication, security, scalability, reliability, or deployment.
- Multiple materially different architectural approaches exist.
- The user explicitly invokes `/grill-me`.

## 1. Execution Protocol

Follow STEP_1 → STEP_2 → STEP_3 in order. You MUST NOT skip steps.

### STEP_1: Context Loading and Merging

Do not generate the final design during this step.

1. Extract from the user request: Intended outcome, Functional scope, Explicit constraints, Non-functional requirements.
2. Inspect the codebase using available read-only tools.
3. Build the Unified Context.
4. Every important codebase fact MUST reference its EXACT source path. Never present an inference or assumption as a confirmed fact. Any new/non-existent component must be prefixed with `[PROPOSED]`.

### STEP_2: Architectural Critique and Approval Gate

1. Identify missing information that materially affects system boundaries, data ownership, contracts, or security.
2. Resolve code-discoverable gaps through read-only inspection before asking the user.
3. If blocking or high-impact decisions remain, you MUST ask the user using the interactive question tool (if available) or via chat. Provide the recommended option and consequences.
4. **APPROVAL GATE (CRITICAL):** Before moving to STEP_3, you MUST present a "Design Readiness Summary". You are STRICTLY FORBIDDEN from generating or saving the blueprint until the user explicitly replies with "YES" or clear approval. If there is no explicit approval, remain in STEP_2 and output `WAITING_FOR_APPROVAL`.

### STEP_3: Blueprint Generation and Persistence

**STRICT OUTPUT PROTOCOL (CRITICAL):**
1. You MUST persist the completed four-section blueprint (Overview, Workflows, Decisions, Control Harness) EXCLUSIVELY to this canonical file path:
   `<repository-root>/docs/features/<feature-id>/architecture.md`
2. You may create the feature directory and write `architecture.md`. If it already exists, update it.
3. **ANTI-DUMP RULE:** You MUST NOT output, print, or summarize the blueprint content, headers, or Mermaid diagrams into the chat interface. 
4. After writing the file, you MUST use the read tool to read the file back into your context to verify it contains all four sections.
5. Your final chat response MUST be a short summary containing EXACTLY AND ONLY these four items:
   - Feature ID
   - Blueprint Status (Created / Updated)
   - Absolute File Path
   - Remaining Assumptions / Risks
6. STOP. You MUST NOT invite, ask, or suggest the user to "proceed", "execute", or move to any next step. Stop immediately after outputting the 4 items.

## 1. Architecture Overview (File Content Only)
Include:
- System boundary and affected components
- Responsibilities and dependency direction
- Data ownership
- Integration points
- Components added, changed, reused
- One Mermaid component/flowchart diagram
- One Mermaid sequence diagram for the primary runtime flow

## 2. Significant Workflows and Contracts (File Content Only)
Include:
- Mermaid `flowchart TD` or `stateDiagram-v2` diagrams for architecturally significant workflows
- API, event, database, or frontend/backend contracts
- Transaction, consistency, idempotency timeout, and retry behavior

## 3. Decisions, Risks, and Definition of Done (File Content Only)
For every material architectural decision define: Decision, Reason, Rejected alternatives, Consequences.
Define: Confirmed assumptions, Accepted risks, Unresolved risks, Explicit exclusions, and Definition of Done.

## 4. Control Harness (File Content Only)
Define the following elements to guide future implementation:
1. Outcome (Business and technical target)
2. Verifiable End State (Exact evidence required)
3. Relevant Context (Existing files to inspect, expected new artifacts)
4. Constraints (Immutable boundaries, technology constraints)
5. Validation Loop (Verification steps)
6. Workspace and Logging
7. Stop Rules (Failure/Blocker conditions)