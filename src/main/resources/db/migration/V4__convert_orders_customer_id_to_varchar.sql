-- V4: Convert ORDERS.CUSTOMER_ID from NUMBER to VARCHAR2(100)
-- This aligns the DB schema with the CustomerInfo @Embeddable entity which declares customerId as String.
-- Oracle requires adding a temp column, copying data, dropping old, and renaming.

-- Step 1: Add a temporary VARCHAR2 column
ALTER TABLE ORDERS ADD (CUSTOMER_ID_TMP VARCHAR2(100));

-- Step 2: Copy existing NUMBER data as string (NULL-safe)
UPDATE ORDERS SET CUSTOMER_ID_TMP = TO_CHAR(CUSTOMER_ID) WHERE CUSTOMER_ID IS NOT NULL;

COMMIT;

-- Step 3: Drop the original NUMBER column
ALTER TABLE ORDERS DROP COLUMN CUSTOMER_ID;

-- Step 4: Rename temp column to the original name
ALTER TABLE ORDERS RENAME COLUMN CUSTOMER_ID_TMP TO CUSTOMER_ID;

-- Step 5: (Optional) Recreate the index if it existed
-- DROP INDEX idx_customer_id; (already dropped with column)
CREATE INDEX idx_customer_id ON ORDERS (CUSTOMER_ID);
