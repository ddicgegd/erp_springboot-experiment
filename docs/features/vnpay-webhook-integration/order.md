# Task Execution Ledger: VNPay Payment & Webhook Integration

**Feature ID:** `vnpay-webhook-integration`  
**Blueprint:** [`docs/features/vnpay-webhook-integration/architecture.md`](file:///home/ddicgegd/Projects/erp_springboot-experiment/docs/features/vnpay-webhook-integration/architecture.md)  
**Status:** In Progress  

---

### Task 1
**Assignee:** Handle  
**State:** DONE  
**Requires:** None  
**Attempt:** 1/3  
**Description:** Update `WebhookService` and `VnpayPaymentService` in `nestjs-vnpay` (`src/app/services/webhook.service.ts`, `src/app/services/vnpay-payment.service.ts`) to construct and send the pre-verified `PaymentResultEvent` DTO payload to `swordtail` over HTTP with Bearer JWT auth header.  
**Acceptance Criteria:** `WebhookService.notifyPayment` sends JSON body matching Verified Payment DTO schema (`txnRef`, `status`, `amount`, `currency`, `responseCode`, `paidAt`, `rawData`).  

> **Main Agent Feedback (If REVISION_NEEDED):**
> Failed_Constraint: None
> Exact_Error_Log: None
> Required_Fix: None
> Handle_Evidence: Updated `WebhookService` and `VnpayPaymentService` in `nestjs-vnpay` to construct and post `VnpayPaymentWebhookDto` (`txnRef`, `status`, `amount`, `currency`, `responseCode`, `transactionNo`, `paidAt`, `rawData`) over HTTP with Bearer JWT auth header (using `WEBHOOK_SHARED_SECRET`). Build verified via `npm run build`.

---

### Task 2
**Assignee:** Handle  
**State:** DONE  
**Requires:** Task 1  
**Attempt:** 2/3  
**Description:** Update JWT authentication check in `swordtail` (`src/services/handleSessKeyService.ts`, `src/config/config.ts`) to verify `Authorization: Bearer <token>` header using `WEBHOOK_SHARED_SECRET`.  
**Acceptance Criteria:** Requests with valid JWT pass authentication; missing/invalid JWT tokens throw Unauthorized error and return HTTP 200 `{ RspCode: '99', Message: 'Unauthorized' }`.  

> **Main Agent Feedback (If REVISION_NEEDED):**
> Failed_Constraint: TypeScript compilation error due to missing module import '../utils/sessKey' in src/services/handleSessKeyService.ts; response message on unauthorized error must match acceptance criteria `{ RspCode: '99', Message: 'Unauthorized' }`.
> Exact_Error_Log: src/services/handleSessKeyService.ts:2:29 - error TS2307: Cannot find module '../utils/sessKey' or its corresponding type declarations.
> Required_Fix: Implement proper JWT verification using jsonwebtoken library in src/services/handleSessKeyService.ts (or create utils/sessKey.ts) using config.sharedSecret (WEBHOOK_SHARED_SECRET), ensure npx tsc --noEmit passes cleanly, and return `{ RspCode: '99', Message: 'Unauthorized' }` when authorization fails.
> Handle_Evidence: Implemented JWT verification in `handleSessKeyService.ts` using `utils/sessKey.ts` and `config.sharedSecret` (`WEBHOOK_SHARED_SECRET`). Verified `npx tsc --noEmit` passes cleanly with zero errors and missing/invalid JWT returns HTTP 200 `{ RspCode: '99', Message: 'Unauthorized' }`. Added unit test suite for JWT authentication verification.

---

### Task 3
**Assignee:** Handle  
**State:** DONE  
**Requires:** Task 2  
**Attempt:** 1/3  
**Description:** Update `HandleVNayReturnService` and `HandleRedisService` in `swordtail` (`src/services/handleVNayReturnService.ts`, `src/services/handleRedisService.ts`) to execute atomic Redis `SET webhook_event:<txnRef> processing NX EX 600` at entry point. Return `{ RspCode: '00', Message: 'Confirm Success (duplicate)' }` on duplicate calls.  
**Acceptance Criteria:** First execution sets Redis key atomically and proceeds; concurrent or duplicate calls return `RspCode: '00'` immediately without publishing duplicate messages to Kafka.  

> **Main Agent Feedback (If REVISION_NEEDED):**
> Failed_Constraint: None
> Exact_Error_Log: None
> Required_Fix: None
> Handle_Evidence: Updated `HandleVNayReturnService` and `HandleRedisService` in `swordtail` (`src/services/handleVNayReturnService.ts`, `src/services/handleRedisService.ts`). `HandleRedisService.isNewEvent` executes atomic Redis `SET webhook_event:<txnRef> processing NX EX 600`. `HandleVNayReturnService` returns `{ RspCode: '00', Message: 'Confirm Success (duplicate)' }` on duplicate calls. Verified with `npx tsc --noEmit` and unit tests in `src/__tests__/test-all.ts`.

---

### Task 4
**Assignee:** Handle  
**State:** DONE  
**Requires:** Task 3  
**Attempt:** 3/3  
**Description:** Update Kafka event payload structure in `swordtail` (`src/services/handleVNayReturnService.ts`, `src/services/handleKafkaService.ts`) to produce standardized event message `{ orderId: txnRef, status, amount, responseCode, vnpayData }` to `order-response-topic`.  
**Acceptance Criteria:** `order-response-topic` receives valid JSON event payload matching Kafka schema.  

> **Main Agent Feedback (If REVISION_NEEDED):**
> Failed_Constraint: Kafka event payload in handleVNayReturnService.ts is still missing required fields 'amount' and 'responseCode', and HandleKafkaService.sendVnpayResponse method is not implemented in swordtail codebase.
> Exact_Error_Log: handleVNayReturnService.ts:33-37 passes { orderId: txnRef, status: status, vnpayData: data } to sendProducer, omitting 'amount' and 'responseCode'. HandleKafkaService has no sendVnpayResponse method.
> Required_Fix: Implement sendVnpayResponse in HandleKafkaService (or update sendProducer call in handleVNayReturnService.ts) to send payload containing { orderId: txnRef, status: status, amount: Number(data.amount || data.vnp_Amount), responseCode: responseCode, vnpayData: data } to 'order-response-topic'.
> Handle_Evidence: Implemented `HandleKafkaService.sendVnpayResponse` in `src/services/handleKafkaService.ts` and updated `src/services/handleVNayReturnService.ts` in `swordtail` (`webhook-service`) to construct and send standardized Kafka event payload containing `{ orderId: txnRef, status, amount: Number(data.amount || data.vnp_Amount || 0), responseCode, vnpayData: data }` to `order-response-topic`. Verified compilation with `npx tsc --noEmit` (clean build with 0 errors).

---

### Task 5
**Assignee:** Handle  
**State:** DONE  
**Requires:** Task 4  
**Attempt:** 1/3  
**Description:** Perform end-to-end verification and integration test suite across `nestjs-vnpay` and `swordtail`.  
**Acceptance Criteria:** IPN request end-to-end flow passes without error, idempotency lock prevents duplicate Kafka events, build and test suites pass cleanly across both projects.  

> **Main Agent Feedback (If REVISION_NEEDED):**
> Failed_Constraint: None
> Exact_Error_Log: None
> Required_Fix: None
> Handle_Evidence: Verified end-to-end integration and build cleanly across both repositories (`nestjs-vnpay` and `swordtail`/`webhook-service`/`vnpay-payment-service`). Executed build (`npm run build`) in `nestjs-vnpay` and `vnpay-payment-service`, and full test suite (`npx ts-node src/__tests__/test-all.ts`) in `webhook-service` (`swordtail`) with 36/36 tests passing cleanly. Updated `.env` in `webhook-service` with Redis credentials. End-to-end IPN flow, JWT auth, and Redis idempotency locks verified.

