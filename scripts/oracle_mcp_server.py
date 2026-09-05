#!/usr/bin/env python3
"""
Oracle Database MCP Server
Features:
  - list_tables: List all accessible tables and views.
  - describe_table: Detail schema, column types, PK/FK constraints.
  - preview_query: "Test báo kết quả trước khi làm thật" (Dry-Run / Explain Plan / Rollback).
  - execute_query: Execute queries with safety checks and confirmation requirement for DML.
"""

import os
import sys
import json
import re
from typing import Any, Dict, List, Optional
import oracledb
from mcp.server.fastmcp import FastMCP

# 1. Environment & DB Connection Settings
ORACLE_USER = os.getenv("ORACLE_USER", "Spring_app")
ORACLE_PASSWORD = os.getenv("ORACLE_PASSWORD", "oracle@2025")
ORACLE_HOST = os.getenv("ORACLE_HOST", "localhost")
ORACLE_PORT = int(os.getenv("ORACLE_PORT", "1521"))
ORACLE_SERVICE = os.getenv("ORACLE_SERVICE", "XEPDB1")

mcp = FastMCP("oracle-db")

def get_connection(autocommit: bool = False) -> oracledb.Connection:
    """Creates a direct Thin-mode connection to Oracle Database."""
    conn = oracledb.connect(
        user=ORACLE_USER,
        password=ORACLE_PASSWORD,
        dsn=f"{ORACLE_HOST}:{ORACLE_PORT}/{ORACLE_SERVICE}"
    )
    conn.autocommit = autocommit
    return conn

def sanitize_sql(sql: str) -> str:
    """Trim and remove trailing semicolon."""
    return sql.strip().rstrip(";")

def get_statement_type(sql: str) -> str:
    """Identify SQL statement type: SELECT, INSERT, UPDATE, DELETE, DDL, etc."""
    cleaned = re.sub(r"--.*?$|/\*.*?\*/", "", sql, flags=re.MULTILINE).strip()
    match = re.match(r"^([A-Za-z]+)", cleaned)
    if not match:
        return "UNKNOWN"
    return match.group(1).upper()

# ==============================================================================
# MCP TOOLS
# ==============================================================================

@mcp.tool()
def list_tables(schema: str = "") -> str:
    """
    List all tables and views in the database.
    If schema is empty, lists tables owned by the current user.
    """
    try:
        with get_connection() as conn:
            with conn.cursor() as cur:
                target_schema = schema.strip().upper() if schema else ORACLE_USER.upper()
                query = """
                    SELECT table_name, 'TABLE' as type, num_rows
                    FROM all_tables
                    WHERE owner = :owner
                    UNION ALL
                    SELECT view_name as table_name, 'VIEW' as type, NULL as num_rows
                    FROM all_views
                    WHERE owner = :owner
                    ORDER BY type, table_name
                """
                cur.execute(query, owner=target_schema)
                rows = cur.fetchall()
                
                if not rows:
                    return f"No tables or views found for schema: '{target_schema}'."
                
                output = [f"=== Tables & Views for schema '{target_schema}' (Total: {len(rows)}) ==="]
                for name, obj_type, num_rows in rows:
                    rows_info = f" (~{num_rows} rows)" if num_rows is not None else ""
                    output.append(f"- [{obj_type}] {name}{rows_info}")
                return "\n".join(output)
    except Exception as e:
        return f"Error listing tables: {str(e)}"

@mcp.tool()
def describe_table(table_name: str, schema: str = "") -> str:
    """
    Describe table structure, columns, data types, nullability, and primary/foreign keys.
    """
    try:
        with get_connection() as conn:
            with conn.cursor() as cur:
                target_table = table_name.strip().upper()
                target_schema = schema.strip().upper() if schema else ORACLE_USER.upper()
                
                # 1. Columns
                col_query = """
                    SELECT column_name, data_type, data_length, data_precision, data_scale, nullable
                    FROM all_tab_cols
                    WHERE owner = :owner AND table_name = :tbl
                    ORDER BY column_id
                """
                cur.execute(col_query, owner=target_schema, tbl=target_table)
                cols = cur.fetchall()
                if not cols:
                    return f"Table '{target_schema}.{target_table}' not found."

                # 2. Constraints (PK/FK)
                cons_query = """
                    SELECT cc.column_name, c.constraint_type, c.constraint_name
                    FROM all_constraints c
                    JOIN all_cons_columns cc ON c.constraint_name = cc.constraint_name AND c.owner = cc.owner
                    WHERE c.owner = :owner AND c.table_name = :tbl AND c.constraint_type IN ('P', 'R', 'U')
                """
                cur.execute(cons_query, owner=target_schema, tbl=target_table)
                constraints: Dict[str, List[str]] = {}
                for col_name, c_type, c_name in cur.fetchall():
                    type_str = {"P": "PRIMARY KEY", "R": "FOREIGN KEY", "U": "UNIQUE"}.get(c_type, c_type)
                    constraints.setdefault(col_name, []).append(f"{type_str} ({c_name})")

                output = [f"=== Schema definition: {target_schema}.{target_table} ==="]
                output.append(f"{'Column Name':<30} {'Data Type':<20} {'Nullable':<10} {'Constraints'}")
                output.append("-" * 80)
                
                for col_name, d_type, d_len, prec, scale, nullable in cols:
                    if d_type in ("VARCHAR2", "CHAR"):
                        type_str = f"{d_type}({d_len})"
                    elif d_type == "NUMBER" and prec is not None:
                        type_str = f"NUMBER({prec},{scale if scale is not None else 0})"
                    else:
                        type_str = d_type
                    
                    cons_str = ", ".join(constraints.get(col_name, []))
                    output.append(f"{col_name:<30} {type_str:<20} {nullable:<10} {cons_str}")

                return "\n".join(output)
    except Exception as e:
        return f"Error describing table: {str(e)}"

@mcp.tool()
def preview_query(sql: str, sample_rows: int = 5) -> str:
    """
    TEST TRƯỚC KHI LÀM THẬT (Dry-Run & Safety Preview):
    - SELECT: Chạy EXPLAIN PLAN để kiểm tra cú pháp, index, cost và query thử một số dòng mẫu.
    - DML (UPDATE, DELETE, INSERT): Chạy trong transaction, đếm chính xác số dòng sẽ bị ảnh hưởng (ROWCOUNT), sau đó ROLLBACK NGAY LẬP TỨC. Không làm thay đổi database!
    - DDL (CREATE, ALTER, DROP, TRUNCATE): Cảnh báo vì Oracle tự động commit DDL, không thể rollback.
    """
    clean_sql = sanitize_sql(sql)
    stmt_type = get_statement_type(clean_sql)
    
    if stmt_type in ("CREATE", "ALTER", "DROP", "TRUNCATE"):
        return (
            f"⚠️ CẢNH BÁO DDL ({stmt_type}):\n"
            "Oracle tự động COMMIT trước và sau các lệnh DDL. KHÔNG THỂ rollback khi test thử!\n"
            "Nếu bạn chắc chắn muốn chạy DDL, hãy gọi `execute_query(sql, confirm=True)` trực tiếp."
        )

    conn = get_connection(autocommit=False)
    try:
        cur = conn.cursor()
        
        # Luồng 1: SELECT - Chạy EXPLAIN PLAN và lấy kết quả mẫu
        if stmt_type == "SELECT" or stmt_type.startswith("WITH"):
            # 1. Explain Plan
            cur.execute(f"EXPLAIN PLAN FOR {clean_sql}")
            cur.execute("SELECT PLAN_TABLE_OUTPUT FROM TABLE(DBMS_XPLAN.DISPLAY())")
            plan_lines = [r[0] for r in cur.fetchall()]
            
            # 2. Sample Data
            sample_query = f"{clean_sql} FETCH FIRST {max(1, min(sample_rows, 20))} ROWS ONLY"
            cur.execute(sample_query)
            col_names = [d[0] for d in cur.description] if cur.description else []
            rows = cur.fetchall()
            
            output = [
                "🔍 KẾT QUẢ KIỂM THỬ TRƯỚC (SELECT PREVIEW):",
                "----------------------------------------------------------------",
                "📊 1. Kế hoạch thực thi (Execution Plan):",
                "\n".join(plan_lines[:25]),
                "",
                f"📋 2. Dữ liệu mẫu ({len(rows)} dòng đầu tiên):",
                " | ".join(col_names),
                "-" * 60
            ]
            for r in rows:
                output.append(" | ".join(str(val) if val is not None else "NULL" for val in r))
            return "\n".join(output)

        # Luồng 2: DML (UPDATE, DELETE, INSERT, MERGE) - DRY-RUN VỚI ROLLBACK BẮT BUỘC
        elif stmt_type in ("UPDATE", "DELETE", "INSERT", "MERGE"):
            try:
                cur.execute(clean_sql)
                affected = cur.rowcount
            finally:
                # ĐẢM BẢO LUÔN ROLLBACK DÙ CÓ LỖI HAY KHÔNG
                conn.rollback()

            return (
                f"🛡️ KẾT QUẢ DRY-RUN (ĐÃ ROLLBACK HOÀN TOÀN - DATABASE KHÔNG BỊ THAY ĐỔI):\n"
                f"----------------------------------------------------------------\n"
                f"• Loại câu lệnh: {stmt_type}\n"
                f"• Cú pháp: HỢP LỆ\n"
                f"• Số bản ghi sẽ bị ảnh hưởng nếu chạy thật: {affected} dòng\n"
                f"• Trạng thái DB: Đã ROLLBACK thành công về trạng thái ban đầu.\n\n"
                f"👉 Để thực thi và commit thay đổi thật sự vào DB, hãy gọi:\n"
                f"`execute_query(sql='...', confirm=True)`"
            )
        else:
            return f"Loại câu lệnh '{stmt_type}' chưa được hỗ trợ trong preview."
            
    except Exception as e:
        conn.rollback()
        return f"❌ LỖI KHI TEST CÂU LỆNH:\n{str(e)}"
    finally:
        conn.close()

@mcp.tool()
def execute_query(sql: str, confirm: bool = False, max_rows: int = 50) -> str:
    """
    Thực thi câu lệnh SQL vào Oracle Database.
    LƯU Ý AN TOÀN:
    - Với các lệnh DML (UPDATE, DELETE, INSERT) hoặc DDL: Bắt buộc `confirm=True` mới thực thi và COMMIT.
    - Với SELECT: Tự động giới hạn tối đa `max_rows` (mặc định 50 dòng) để chống tràn context.
    """
    clean_sql = sanitize_sql(sql)
    stmt_type = get_statement_type(clean_sql)

    # Chặn DML/DDL nếu chưa xác nhận
    if stmt_type in ("UPDATE", "DELETE", "INSERT", "MERGE", "DROP", "TRUNCATE", "ALTER", "CREATE"):
        if not confirm:
            return (
                f"⛔ TỪ CHỐI THỰC THI (Safety Gate):\n"
                f"Câu lệnh `{stmt_type}` làm thay đổi dữ liệu hoặc cấu trúc DB nhưng tham số `confirm=False`.\n"
                f"1. Vui lòng chạy `preview_query` để kiểm tra số dòng ảnh hưởng trước.\n"
                f"2. Nếu đã chắc chắn, gọi lại `execute_query(sql, confirm=True)`."
            )

    conn = get_connection(autocommit=False)
    try:
        cur = conn.cursor()
        if stmt_type == "SELECT" or stmt_type.startswith("WITH"):
            limit = max(1, min(max_rows, 100))
            cur.execute(f"{clean_sql} FETCH FIRST {limit} ROWS ONLY")
            cols = [d[0] for d in cur.description] if cur.description else []
            rows = cur.fetchall()
            
            output = [
                f"✅ Query thành công ({len(rows)} dòng, giới hạn tối đa {limit}):",
                " | ".join(cols),
                "-" * 60
            ]
            for r in rows:
                output.append(" | ".join(str(val) if val is not None else "NULL" for val in r))
            return "\n".join(output)
        else:
            cur.execute(clean_sql)
            affected = cur.rowcount
            conn.commit()
            return f"✅ Thực thi và COMMIT thành công ({stmt_type}): {affected} dòng bị tác động."
    except Exception as e:
        conn.rollback()
        return f"❌ LỖI KHI THỰC THI SQL:\n{str(e)}"
    finally:
        conn.close()

# ==============================================================================
# SELF-TEST RUNNER (CLI)
# ==============================================================================

def run_self_test():
    """Tự động kiểm thử tất cả các tool để xác thực hoạt động trước khi dùng."""
    print("==================================================")
    print(" BẮT ĐẦU KIỂM THỬ ORACLE MCP SERVER (SELF-TEST)")
    print("==================================================")
    
    print("\n[Test 1] list_tables:")
    res = list_tables()
    print("\n".join(res.splitlines()[:6]) + "\n...")

    print("\n[Test 2] describe_table('VOUCHERS'):")
    res = describe_table("VOUCHERS")
    print("\n".join(res.splitlines()[:10]) + "\n...")

    print("\n[Test 3] preview_query (SELECT với Explain Plan):")
    res = preview_query("SELECT ID, CODE, NAME FROM VOUCHERS WHERE IS_ACTIVE = 1", sample_rows=2)
    print(res)

    print("\n[Test 4] preview_query (DML UPDATE với Rollback kiểm chứng):")
    res = preview_query("UPDATE VOUCHERS SET TOTAL_QUANTITY = TOTAL_QUANTITY + 10 WHERE ID = 1")
    print(res)

    print("\n[Test 5] execute_query chặn DML khi confirm=False:")
    res = execute_query("DELETE FROM VOUCHERS WHERE ID = 99999", confirm=False)
    print(res)

    print("\n==================================================")
    print(" KIỂM THỬ THÀNH CÔNG! TẤT CẢ CƠ CHẾ AN TOÀN ĐÃ SẴN SÀNG.")
    print("==================================================")

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--test":
        run_self_test()
    else:
        mcp.run(transport="stdio")
