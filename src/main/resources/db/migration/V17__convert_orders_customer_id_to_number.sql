-- V17: Convert ORDERS.CUSTOMER_ID from VARCHAR2(100) to NUMBER(19,0)
-- This aligns the DB schema with the CustomerInfo @Embeddable entity which declares customerId as Long.

-- Step 1: Add a temporary NUMBER(19,0) column
ALTER TABLE ORDERS ADD (CUSTOMER_ID_TMP NUMBER(19,0));

-- Step 2: Copy existing VARCHAR2 data as NUMBER (NULL-safe and error-tolerant in Oracle 21c)
UPDATE ORDERS SET CUSTOMER_ID_TMP = TO_NUMBER(CUSTOMER_ID DEFAULT NULL ON CONVERSION ERROR) WHERE CUSTOMER_ID IS NOT NULL;

COMMIT;

-- Step 3: Drop the original VARCHAR2 column
ALTER TABLE ORDERS DROP COLUMN CUSTOMER_ID;

-- Step 4: Rename temp column to the original name
ALTER TABLE ORDERS RENAME COLUMN CUSTOMER_ID_TMP TO CUSTOMER_ID;

-- Step 5: Recreate index on CUSTOMER_ID
CREATE INDEX idx_customer_id ON ORDERS (CUSTOMER_ID);
