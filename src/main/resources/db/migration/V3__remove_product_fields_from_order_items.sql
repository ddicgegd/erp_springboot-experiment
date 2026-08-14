-- V3: product_id, product_name, product_sku columns in ORDER_ITEMS are already nullable.
-- No DDL changes required. This script is intentionally a no-op.
-- ORA-01451 was thrown on previous attempt: column already allows NULL.
SELECT 1 FROM DUAL;
