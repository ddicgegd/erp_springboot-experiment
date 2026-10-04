## Question
How exactly does Fineract handle short-term (grace periods + flat fees) vs long-term (overdue interest multiplier) penalties, and how should we port that logic into a Spring Boot scheduled task (cron) or event-based trigger?

**Context:** User needs penalties for short-term and long-term delays. We need to decide if we calculate penalties dynamically on-read, or persist them via a daily EOD (End of Day) batch job like Fineract's COB (Close of Business) jobs.

**Labels:** `wayfinder:research`, `wayfinder:prototype`
**Status:** Open
**Blocked by:** 01-domain-model-prototype.md