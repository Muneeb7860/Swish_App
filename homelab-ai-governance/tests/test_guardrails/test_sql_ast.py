"""Unit tests for AST SQL validator."""

from __future__ import annotations

from governance.guardrails.sql_ast import detect_unsafe_sql, is_safe_sql


def test_is_safe_sql_valid_select():
    assert is_safe_sql("SELECT * FROM users WHERE id = 1;") is True


def test_is_safe_sql_drop_table():
    assert is_safe_sql("DROP TABLE customers;") is False


def test_is_safe_sql_drop_database():
    assert is_safe_sql("DROP DATABASE production;") is False


def test_is_safe_sql_truncate():
    assert is_safe_sql("TRUNCATE TABLE orders;") is False


def test_is_safe_sql_unbounded_delete():
    assert is_safe_sql("DELETE FROM orders;") is False


def test_is_safe_sql_safe_delete():
    assert is_safe_sql("DELETE FROM orders WHERE status = 'cancelled';") is True


def test_detect_unsafe_sql_in_markdown():
    content = "Here is the query:\n```sql\nDROP TABLE orders;\n```"
    assert detect_unsafe_sql({}, content) is True
