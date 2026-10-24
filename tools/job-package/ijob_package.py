# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT
"""Portable job packages through the same authenticated API used by the web wizard."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

MAX_BYTES = 16 * 1024 * 1024


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise RuntimeError("API redirects are not permitted")


class Api:
    def __init__(self, config):
        parsed = urllib.parse.urlsplit(config["url"])
        if (parsed.scheme not in ("http", "https") or not parsed.hostname
                or parsed.username or parsed.password or parsed.query or parsed.fragment
                or parsed.path not in ("", "/")):
            raise ValueError("Invalid API origin")
        if parsed.scheme == "http" and parsed.hostname not in ("127.0.0.1", "localhost", "::1"):
            raise ValueError("Remote API origins require HTTPS")
        self.base = config["url"].rstrip("/")
        self.headers = {"Content-Type": "application/json", "Accept-Language": "en-US"}
        self.opener = urllib.request.build_opener(NoRedirect())
        login = self.call("POST", "/api/auth/login", {
            "username": config["username"], "password": config["password"]})
        self.headers[login["tokenName"]] = login["tokenValue"]

    def call(self, method, path, data=None):
        body = None if data is None else json.dumps(data, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(self.base + path, body, self.headers, method=method)
        try:
            with self.opener.open(request, timeout=120) as response:
                raw = response.read(MAX_BYTES * 3 + 1)
                if len(raw) > MAX_BYTES * 3:
                    raise RuntimeError("API response limit exceeded")
                result = json.loads(raw)
        except urllib.error.HTTPError as error:
            raise RuntimeError("API HTTP " + str(error.code)) from None
        except urllib.error.URLError:
            raise RuntimeError("API connection failed; check the private connection file") from None
        if not result.get("success", False):
            raise RuntimeError("API operation rejected")
        return result.get("data")


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def content(path):
    with Path(path).open("rb") as stream:
        value = stream.read(MAX_BYTES + 1)
    if len(value) > MAX_BYTES:
        raise ValueError("Package limit is 16 MiB")
    return base64.b64encode(value).decode("ascii")


def write_new(path, value):
    data = value if isinstance(value, bytes) else json.dumps(value, ensure_ascii=False, indent=2).encode("utf-8")
    with Path(path).open("xb") as stream:
        stream.write(data)


def parser():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--connection", required=True, help="Private JSON file: url, username, password; never put secrets in arguments")
    sub = p.add_subparsers(dest="command", required=True)
    export = sub.add_parser("export")
    export.add_argument("--kind", choices=("TASK", "JOB"), required=True)
    export.add_argument("--ids", nargs="+", required=True)
    export.add_argument("--resources", action="store_true")
    export.add_argument("--schedules", action="store_true")
    export.add_argument("--notifications", action="store_true")
    export.add_argument("--out", required=True)
    for name in ("inspect", "plan"):
        command = sub.add_parser(name)
        command.add_argument("--bundle", required=True)
        command.add_argument("--out", required=True)
        if name == "plan":
            command.add_argument("--choices", help="Private JSON mapping file; credentials are never copied to the report")
            command.add_argument("--reviewed-environment", action="store_true")
    for name in ("apply", "verify"):
        command = sub.add_parser(name)
        command.add_argument("--plan", required=True)
        command.add_argument("--out", required=True)
    sub.add_parser("records").add_argument("--out", required=True)
    return p


def main(argv=None):
    args = parser().parse_args(argv)
    # Refuse overwriting a report before a potentially mutating request.
    if Path(args.out).exists():
        raise ValueError("Output already exists; choose a new path")
    api = Api(read_json(args.connection))
    prefix = "/api/job-packages"
    status = None
    if args.command == "export":
        if any(not i.isdecimal() or int(i) <= 0 for i in args.ids):
            raise ValueError("Object IDs must be positive integers")
        result = api.call("POST", prefix + "/export", {
            "roots": [{"kind": args.kind, "id": i} for i in args.ids],
            "includeResources": args.resources, "includeSchedules": args.schedules,
            "includeNotifications": args.notifications})
        data = base64.b64decode(result["content"], validate=True)
        if hashlib.sha256(data).hexdigest() != result["sha256"]:
            raise ValueError("Package checksum mismatch")
        write_new(args.out, data)
    elif args.command in ("inspect", "plan"):
        body = {"content": content(args.bundle)}
        endpoint = "/inspect"
        if args.command == "plan":
            body.update(choices=read_json(args.choices) if args.choices else {}, reviewedEnvironment=args.reviewed_environment)
            endpoint = "/plans"
        result = api.call("POST", prefix + endpoint, body)
        write_new(args.out, result)
        status = result.get("status")
        if "id" in result:
            print("Plan: " + result["id"])
    elif args.command in ("apply", "verify"):
        if str(uuid.UUID(args.plan)) != args.plan:
            raise ValueError("Invalid plan ID")
        endpoint = prefix + "/plans/" + args.plan
        result = api.call("POST" if args.command == "apply" else "GET", endpoint + ("/apply" if args.command == "apply" else "/verify"))
        write_new(args.out, result)
        status = result["status"]
    else:
        write_new(args.out, api.call("GET", prefix + "/records"))
    if status:
        print("Status: " + status)
    if status in ("BLOCKED", "PARTIAL") or args.command == "verify" and status != "VERIFIED":
        return 3
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, KeyError, OSError, RuntimeError) as error:
        # Parser/network exception details can contain URLs or source fragments.
        print("Failed: " + type(error).__name__ + "; check connection, package and report paths", file=sys.stderr)
        sys.exit(2)
