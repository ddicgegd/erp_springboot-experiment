## Destination
A working Code Prototype in `erp_springboot-experiment` that proves the core Loan and Cashflow Management logic (borrowing, collateral management, repayment scheduling, short/long-term penalty triggers, and payment processing), taking inspiration from Apache Fineract's domain models.

## Notes
- Domain: Loan lifecycle, Cashflow Management, Loan Scheduling, Penalties.
- Skills: `domain-modeling`, `prototype`, `research`.
- This is a codebase prototype: focus on Spring Boot entities, core services, and tests proving the logic works, avoiding over-engineered UI or heavy 3rd-party integrations upfront.

## Decisions so far

## Not yet specified
- How are payments ingested and allocated (e.g., does a payment clear Penalties first, then Interest, then Principal?)
- What specific collateral details need tracking (beyond a simple text reference and value) to unblock the prototype?
- How to structure the API for the frontend to easily fetch the borrower's "due right now" vs "total outstanding".

## Out of scope
- Full integration with real Payment Gateways (Momo/VNPay); we will use internal mock/manual payment endpoints.
- Complex Collateral Asset Valuation systems (depreciation, external audits).