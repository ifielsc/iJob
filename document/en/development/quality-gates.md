# What builds, tests and runtime acceptance prove

[简体中文](../../zh-CN/development/quality-gates.md) · [Manual home](../README.md)

Installers with a complete bundle do not need this chapter. Developers/release maintainers use these checks to assess candidates: compilation proves buildability, tests prove their coverage, and runtime acceptance proves specified environments/scenarios. Skipped/unexecuted checks are not passes.

Source builds use Python 3.11+, JDK 21, Maven 3.9.11, Node 24.14.1 and pnpm 11.19.0. Begin development with root AGENTS.md and document/standards/README.md; this guide does not replace maintained engineering standards.

## Quality entry points

In develop/web/ijob:

```sh
pnpm install --frozen-lockfile
pnpm check
pnpm exec playwright install chromium
pnpm exec playwright test --config=playwright.config.ts
pnpm exec playwright test --config=playwright.production.config.ts
```

For local runs, set PLAYWRIGHT_JUNIT_OUTPUT_FILE and --output to new external evidence directories and reject occupied ports. Linux initially needs Playwright system libraries. pnpm check covers runtime, types, lint, module boundaries, formatting, unit tests, production build and bundle budgets. Browser tests run separately.

Run the full backend/Agent gate from the repository root with PowerShell, JDK/Maven, Python and tools/ci/requirements.txt installed:

```powershell
./develop/api/ijob/tools/verify-backend.ps1 -RunId candidate-backend-001 -ReportRoot /absolute/new/reports -TemporaryRoot /absolute/new/temp
```

This complete gate requires a clone containing the pinned historical commit to restore and verify historical SQL test resources. A source ZIP builds new-install distributions but does not establish passage of history-dependent migration gates. Do not copy historical SQL back into application sources. Only the two RealMessageSourcesTest checks may be expected skips in the basic environment; report them separately.

Current document/ci/basic-ci.md and isolated-integration.md describe local HTTP/SQLite suites, seven real-backend Full browser configurations, database matrices and container runtime acceptance. Use new environments, accounts and directories; never depend on maintainer demo or old QA services.

Use appropriate local checks for daily changes. Push/PR runs only lightweight repository checks. Manual suite=backend/frontend selects the complete module; suite=basic runs all seven jobs; suite=docker includes backend/frontend prerequisites. Service management, cross-platform, shared security/dependency/toolchain and CI changes require complete Basic at the stage candidate. Final candidates also require Full and the applicable runtime matrix. Unselected jobs are unexecuted, not passes inferred from a green workflow. Complete test coverage and candidate gates remain available and required.

## Documentation and distribution checks

```sh
python tools/distribution/docs.py --check
python -m unittest discover -s tools/distribution -p "test_*.py"
python tools/repository/check-license.py
```

Configuration tables originate in the setup tool's user-settings.tsv. Update implementation, validation, both languages and tests together, then run python tools/distribution/docs.py --write. --check verifies page pairing, generated tables and local links. Do not translate commands, configuration keys or version identifiers; use the manual's glossary.

Register new files in the exact .repository/allowlist.json and regenerate .gitignore. Never bypass scanning with force-add. Packaging copies only this allowlist and refuses existing output directories; build snapshots are not additional maintained source trees. No packaging action publishes releases, pushes images or changes repository visibility.

## Candidate acceptance

Bind CI, fresh installation, configuration changes, lifecycle, example runs, backup/restore and upgrade/rollback evidence to the same source and final artifacts. Retain failures/retries and distinguish passed, skipped and unexecuted checks. Successful compilation is not acceptance.

Public redistribution still requires resolution of existing licensing and vulnerability findings: vdirs license-text provenance, MySQL/Oracle driver distribution conditions, image/toolchain findings, and final SBOM/NOTICE reconciliation. Keep the repository and candidates private; do not create a public release before these are approved.
