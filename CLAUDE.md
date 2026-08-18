# Project Instructions for AI Agents & Developers

## Tech Stack
- **Language**: Java 21 (LTS)
- **Framework**: Spring Boot 3.x, Spring Security (JWT), Spring Data JPA
- **Datastores**: Oracle Database XE 21c (Primary ACID DB), Redis (Cart & Cache), MinIO (S3-compatible Storage)
- **Messaging**: Apache Kafka 3.8 (KRaft mode, SASL_PLAINTEXT auth)
- **Integration**: Apache Fineract (Core Banking & Accounting Ledger), VNPay (Payment Gateway)
- **Build Tool**: Maven Wrapper (`./mvnw`)

## Build & Run Commands
- Start Infrastructure: `docker compose up -d`
- Run Application: `./mvnw spring-boot:run`
- Build Package: `./mvnw clean package -DskipTests`
- Run Tests: `./mvnw test`
- Single Test: `./mvnw test -Dtest=ClassNameTest`

## Multi-Notebook Knowledge Base (Google NotebookLM)
The project maintains a specialized **Multi-Notebook Knowledge Base** on Google NotebookLM (shared tag: `erp-system`):
- `erp-arch` (or `erp`): Core system architecture, request lifecycles, and ADRs (001–005).
- `erp-api`: Full REST API specifications, DTOs, response schemas, and cURL examples.
- `erp-fineract`: Core Banking, accounting journal entries, client mapping, and Kafka events.

### Agent Grounding Instructions:
Before designing new endpoints, refactoring core components, or changing data models:
1. **Query Specific Domain**:
   - `nlm notebook query erp-arch "<architecture question>"`
   - `nlm notebook query erp-api "<endpoint specification question>"`
   - `nlm notebook query erp-fineract "<banking/accounting question>"`
2. **Cross-Domain Query (Across all notebooks)**:
   - `nlm cross query "<cross-domain question>" --tags erp-system`

## Code Conventions
- **Naming**: `*Controller` (REST), `*Service` / `*ServiceImpl` (Logic), `*Repository` (JPA), `*Request` / `*Response` / `*DTO` (Data transfer).
- **Error Handling**: Never return generic 500 errors. Throw `BusinessException(ErrorCode.XYZ)`. Handled by `GlobalExceptionHandler`.
- **Transactions**: Service methods modifying DB state must have `@Transactional`. External calls (Kafka, MinIO, Fineract) happen post-commit or via Kafka event listeners.
- **Module Boundaries**: Do not inject repositories of another module directly. Always use the other module's public Service interface or Domain Events.
