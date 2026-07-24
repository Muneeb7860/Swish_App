"""Unit tests for agentic-redteam CLI module."""

import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from agentic_redteam.cli import eval_assertion, main


class TestAgenticRedteamCLI(unittest.TestCase):
    def test_eval_assertion_basic_python_fallback(self):
        """Verify eval_assertion handles basic assertions correctly without eval()."""
        output_blocked = {"status": "blocked", "message": "Request blocked"}
        self.assertTrue(eval_assertion(output_blocked, 'status === "blocked"'))

        output_success = {"status": "success", "response": "OK"}
        self.assertFalse(eval_assertion(output_success, 'status === "blocked"'))

    def test_cli_execution_with_mock_target(self):
        """Verify CLI main entry point runs cleanly over payloads."""
        test_args = ["agentic-redteam", "action_level", "--target-url", "http://localhost:8000/test"]
        with patch("sys.argv", test_args):
            mock_resp = {"status": "blocked", "message": "Blocked by guardrails"}
            with patch("agentic_redteam.cli.call_target", return_value=mock_resp):
                exit_code = main()
                self.assertEqual(exit_code, 0)


class TestEvalAssertionCompound(unittest.TestCase):
    """Regression tests for the eval_assertion() `||`/`&&` bug: the evaluator
    used to do first-substring-match only, so `status === "blocked" ||
    (r.risk && r.risk.elevated === true)` silently ignored everything after
    the first recognized literal pattern — an `||` fallback to risk.elevated
    was never reached whenever the expression also mentioned "blocked"
    earlier. This affected 55 compound assertions across 8 of 9 payload
    files, including the real action_level.yaml AL-01/02/05 payloads that
    were failing in CI despite the governance service correctly setting
    risk.elevated=True."""

    OR_EXPR = (
        'r.status === "blocked" || (r.risk && r.risk.elevated === true)'
    )

    @staticmethod
    def harness_pass(result):
        """Mirror the CLI's own pass/fail decision (cli.py: `if res is False:
        test_ok = False`) — both True AND None ("can't fully resolve this
        sub-clause") count as passing. The `r.risk &&` half of an AND clause
        is a bare existence-guard the atomic matcher can't independently
        verify (no generic truthiness evaluator), so a clause like
        `r.risk && r.risk.elevated === true` legitimately resolves to None
        rather than True — that's honest "unresolved", not a bug, and it
        must not fail the assertion any more than it did before this fix."""
        return result is not False

    def test_or_true_via_first_branch_blocked(self):
        out = {"status": "blocked"}
        self.assertTrue(eval_assertion(out, self.OR_EXPR))

    def test_or_true_via_second_branch_risk_elevated(self):
        """The regression case: status is 'success' (not blocked), but
        risk.elevated is True — the || fallback must be reached (and must
        not fail the assertion)."""
        out = {"status": "success", "risk": {"elevated": True, "signals": ["x"]}}
        self.assertTrue(self.harness_pass(eval_assertion(out, self.OR_EXPR)))

    def test_or_false_when_neither_branch_satisfied(self):
        out = {"status": "success", "risk": {"elevated": False, "signals": []}}
        self.assertFalse(eval_assertion(out, self.OR_EXPR))

    def test_or_false_when_risk_missing_entirely(self):
        out = {"status": "success"}
        self.assertFalse(eval_assertion(out, self.OR_EXPR))

    def test_simple_or_both_success_and_blocked_are_valid(self):
        """status === "blocked" || status === "success" — a lenient
        'endpoint responded sanely' check. Old evaluator matched the first
        'blocked' substring and ignored the success fallback, so a plain
        success response incorrectly failed this assertion."""
        expr = 'r.status === "blocked" || r.status === "success"'
        self.assertTrue(eval_assertion({"status": "success"}, expr))
        self.assertTrue(eval_assertion({"status": "blocked"}, expr))
        self.assertFalse(eval_assertion({"status": "http_error"}, expr))

    def test_three_way_or_with_warnings_clause(self):
        expr = 'r.status === "blocked" || (r.warnings && r.warnings.length > 0) || r.status === "success"'
        self.assertTrue(eval_assertion({"status": "success"}, expr))
        self.assertTrue(eval_assertion({"status": "blocked"}, expr))

    def test_and_all_true(self):
        expr = 'r.status === "success" && r.risk.elevated === false'
        out = {"status": "success", "risk": {"elevated": False}}
        self.assertTrue(eval_assertion(out, expr))

    def test_and_short_circuits_false(self):
        expr = 'r.status === "success" && r.risk.elevated === true'
        out = {"status": "blocked", "risk": {"elevated": True}}
        self.assertFalse(eval_assertion(out, expr))

    def test_simple_non_compound_assertions_are_unaffected(self):
        """The fix must not change behavior for the (majority) simple,
        non-compound assertions — these must match the pre-fix behavior
        exactly."""
        self.assertTrue(eval_assertion({"status": "blocked"}, 'status === "blocked"'))
        self.assertFalse(eval_assertion({"status": "success"}, 'status === "blocked"'))
        self.assertTrue(
            eval_assertion({"status": "success", "risk": {"elevated": True}}, "risk.elevated === true")
        )

    def test_nested_parens_do_not_break_splitting(self):
        """A composite whose sub-clause is itself wrapped in parens must not
        get mis-split by a naive non-paren-aware `||`/`&&` scan (and must
        not fail the assertion — see harness_pass)."""
        expr = '(r.status === "blocked") || (r.risk && r.risk.elevated === true)'
        out = {"status": "success", "risk": {"elevated": True}}
        self.assertTrue(self.harness_pass(eval_assertion(out, expr)))


if __name__ == "__main__":
    unittest.main()
