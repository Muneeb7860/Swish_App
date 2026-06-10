"""Tests for the pre-routing PII scan module."""

from __future__ import annotations

from governance.router.pii_scan import pre_route_pii_scan


def test_pii_scan_clean():
    """Verify that a clean query doesn't trigger the PII routing constraint."""
    res = pre_route_pii_scan("What is the capital of Switzerland?")
    assert res.contains_pii is False
    assert res.local_only is False
    assert len(res.pii_types) == 0


def test_pii_scan_with_email():
    """Verify that email addresses trigger local_only constraint."""
    res = pre_route_pii_scan("My email address is support@example.ch.")
    assert res.contains_pii is True
    assert res.local_only is True
    assert "EMAIL" in res.pii_types


def test_pii_scan_with_credit_card():
    """Verify that credit card numbers trigger local_only constraint."""
    res = pre_route_pii_scan("Please process my payment for card 4111 2222 3333 4444.")
    assert res.contains_pii is True
    assert res.local_only is True
    assert "CREDIT_CARD" in res.pii_types


def test_pii_scan_with_phone():
    """Verify that phone numbers trigger local_only constraint."""
    res = pre_route_pii_scan("Call me at +41-44-123-4567 tomorrow.")
    assert res.contains_pii is True
    assert res.local_only is True
    assert "PHONE_NUMBER" in res.pii_types
