# Rule: Spring Boot & Java Backend Standards
**File Path:** `rules/30-backend-spring.md`

## Trigger

Apply ONLY when you are in the Execution Phase (Phase 2/3) after a plan is confirmed, AND the execution involves Java, Spring Boot, backend logic, APIs, services, controllers, repositories, DTOs, entities, validation, transactions, exceptions, tests, or database interactions.
Do NOT apply this rule during Planning (Phase 1) or for tasks that do not execute Spring Boot logic.

## 1. Context Conservation

Preserve context while still understanding behavior correctly.

* Start by reading interfaces, public method signatures, class headers, DTOs, entities, repository contracts, and nearby tests.
* Read full implementation files when needed to understand behavior, avoid regressions, fix bugs, or modify an existing method safely.
* Do not read unrelated full implementation files just to imitate style.
* Match existing naming conventions, package structure, dependency injection style, validation style, exception style, and transaction patterns.

## 2. Anti-DTO Proliferation Policy

Avoid creating unnecessary request/response classes.

* Reuse existing DTOs when they match the API contract exactly.
* Do not reuse DTOs if doing so leaks fields, mixes request and response semantics, couples unrelated endpoints, or weakens validation.
* Do not create wrapper objects when returning a direct collection, primitive, existing DTO, projection, or page type is sufficient and consistent with the existing API style.
* Create a new DTO only when the API contract, validation, security boundary, or readability requires it.
* When creating a new DTO, prefer Java `record` if the project Java version, serialization stack, and existing module style support records.
* Do not use Lombok `@Data` for DTOs unless the module already uses that style.

## 3. Modern Java Idioms

Use modern Java where it improves clarity.

* Prefer Stream API for simple transformations, filtering, grouping, and mapping.
* Use loops when they are clearer, require early exit, handle checked exceptions, avoid awkward side effects, or are more readable for multi-step logic.
* Use `Optional` primarily for nullable return values and repository lookups.
* Do not use `Optional` for entity fields, DTO fields, or method parameters unless the existing codebase already does so.
* Avoid business logic built around repeated `if (obj != null)` checks. Prefer guard clauses, `Optional`, or explicit validation depending on context.
* Use `var` only when the type is obvious from the right-hand side and readability improves.

## 4. Method Complexity & Clean Code

Do not write long, monolithic methods.

* Validate inputs and invalid states at the top of the method. Throw specific exceptions early.
* Avoid deeply nested `if-else` blocks.
* Keep methods focused on one responsibility.
* If a method exceeds 25-30 lines after implementation, consider extracting private helper methods with descriptive names.
* Hide complex boolean conditions inside well-named private methods when it improves readability.
* Do not extract helpers mechanically if it makes the code harder to trace.

## 5. Spring Boot Specifics

* Prefer constructor injection with `final` fields.
* Use Lombok `@RequiredArgsConstructor` only when Lombok is already used in the module.
* Do not use field injection with `@Autowired`.
* Apply `@Transactional` at the Service layer.
* Use write transactions for methods that modify state.
* Use `@Transactional(readOnly = true)` for read methods when existing convention, lazy loading, consistency, or persistence behavior requires it.
* Keep transactions short.
* Do not perform expensive external API calls inside a database transaction.
* Throw specific domain exceptions where the codebase has domain exception patterns.
* Do not return HTTP `ResponseEntity` from the Service layer. Controllers or `@RestControllerAdvice` should handle HTTP status codes.
