# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT
"""CLI contract and local-file safety tests; no external services are contacted."""
import base64
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, Mock
import uuid

import ijob_package as package


class PackageCliTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="package-cli-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.connection = self.root / "connection.json"
        self.connection.write_text("{}", encoding="utf-8")
        self.output = self.root / "report.json"
        self.plan = str(uuid.uuid4())

    def run_command(self, api, *args):
        with patch.object(package, "Api", return_value=api):
            return package.main(["--connection", str(self.connection), *args,
                                 "--out", str(self.output)])

    def test_export_verifies_bytes_and_preserves_large_ids(self):
        data = b"portable archive fixture"
        api = Mock()
        api.call.return_value = {"content": base64.b64encode(data).decode(),
                                 "sha256": hashlib.sha256(data).hexdigest()}
        self.assertEqual(0, self.run_command(api, "export", "--kind", "JOB",
                                            "--ids", "9007199254740993", "--resources"))
        self.assertEqual(data, self.output.read_bytes())
        body = api.call.call_args.args[2]
        self.assertEqual("9007199254740993", body["roots"][0]["id"])
        self.assertTrue(body["includeResources"])

    def test_bad_checksum_does_not_write_an_archive(self):
        api = Mock()
        api.call.return_value = {"content": "YWJj", "sha256": "wrong"}
        with self.assertRaises(ValueError):
            self.run_command(api, "export", "--kind", "TASK", "--ids", "1")
        self.assertFalse(self.output.exists())

    def test_existing_output_prevents_any_request(self):
        self.output.write_text("preserve", encoding="utf-8")
        api = Mock()
        with self.assertRaises(ValueError):
            self.run_command(api, "apply", "--plan", self.plan)
        api.call.assert_not_called()
        self.assertEqual("preserve", self.output.read_text())

    def test_blocked_plan_has_distinct_exit_code_and_safe_report(self):
        bundle = self.root / "input.zip"
        bundle.write_bytes(b"fixture")
        api = Mock()
        api.call.return_value = {"id": self.plan, "status": "BLOCKED",
                                 "steps": [{"issues": ["MAP_REQUIRED"]}]}
        self.assertEqual(3, self.run_command(api, "plan", "--bundle", str(bundle)))
        self.assertFalse(api.call.call_args.args[2]["reviewedEnvironment"])
        self.assertEqual("BLOCKED", json.loads(self.output.read_text())["status"])

    def test_verify_queries_actual_verification_endpoint(self):
        api = Mock()
        api.call.return_value = {"id": self.plan, "status": "VERIFIED", "changedKeys": []}
        self.assertEqual(0, self.run_command(api, "verify", "--plan", self.plan))
        api.call.assert_called_once_with("GET", "/api/job-packages/plans/" + self.plan + "/verify")

    def test_partial_apply_does_not_automatically_retry(self):
        api = Mock()
        api.call.return_value = {"id": self.plan, "status": "PARTIAL"}
        self.assertEqual(3, self.run_command(api, "apply", "--plan", self.plan))
        self.assertEqual(1, api.call.call_count)

    def test_remote_plain_http_and_embedded_credentials_are_rejected(self):
        for url in ("http://example.invalid", "https://u:p@example.invalid", "https://example.invalid?token=x"):
            with self.subTest(url=url), self.assertRaises(ValueError):
                package.Api({"url": url})

    def test_redirects_cannot_forward_authentication(self):
        with self.assertRaises(RuntimeError):
            package.NoRedirect().redirect_request(None, None, 302, None, {}, "https://example.invalid")

    def test_package_size_is_bounded_before_upload(self):
        path = self.root / "oversized.zip"
        path.write_bytes(b"a" * 17)
        with patch.object(package, "MAX_BYTES", 16), self.assertRaises(ValueError):
            package.content(path)


if __name__ == "__main__":
    unittest.main()
