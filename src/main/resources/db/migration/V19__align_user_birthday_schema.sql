-- V19: Align User Birthday Schema
-- Convert USERS.DATE_OF_BIRTH from TIMESTAMP to DATE for LocalDate mapping
-- Add DOB_UPDATED_AT (TIMESTAMP) and HIDE_BIRTH_YEAR (NUMBER(1)) columns

-- Step 1: Add temporary DATE column for date_of_birth
ALTER TABLE USERS ADD (DATE_OF_BIRTH_TMP DATE);

-- Step 2: Copy existing TIMESTAMP data as DATE
UPDATE USERS SET DATE_OF_BIRTH_TMP = CAST(DATE_OF_BIRTH AS DATE) WHERE DATE_OF_BIRTH IS NOT NULL;
COMMIT;

-- Step 3: Drop the original TIMESTAMP column
ALTER TABLE USERS DROP COLUMN DATE_OF_BIRTH;

-- Step 4: Rename temp column to original name
ALTER TABLE USERS RENAME COLUMN DATE_OF_BIRTH_TMP TO DATE_OF_BIRTH;

-- Step 5: Add birthday lifecycle cooldown column
ALTER TABLE USERS ADD (
    DOB_UPDATED_AT TIMESTAMP
);
COMMIT;
