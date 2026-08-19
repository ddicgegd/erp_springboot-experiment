-- V13: Align VOUCHERS table and ensure schema consistency with flyway_schema_history
-- Oracle DB DDL Migration Script

DECLARE
  v_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'VOUCHERS';
  IF v_count > 0 THEN
    NULL;
  END IF;
END;
/
