# Account sources and daily administration

[简体中文](../../zh-CN/operations/accounts.md) · [Manual home](../README.md)

## Identify authentication mode

| Mode | Login source | Routine maintenance |
| --- | --- | --- |
| STATIC (default) | app.auth.username and BCrypt password-hash | Controlled private-config change and restart |
| DATABASE | Persisted users, roles and status | Create/update/enable users through authorized pages |

Authentication is independent of STANDALONE/CLUSTER runtime mode. Neither authentication choice changes topology automatically. NONE/DEVELOPMENT are not deployment login modes.

STATIC configuration stores a BCrypt hash, not a plaintext password. Generate a replacement with a trusted interactive/stdin tool, avoiding command history, and verify in isolation. An existing cluster includes the hash in compatibility checks and needs a coordinated change plan.

DATABASE login uses persisted users, not the configured username/hash directly. Cluster compatibility may still include those configuration fields; do not remove them casually. Retain at least one enabled administrator, manage users through authorized pages and inspect audits. A bootstrapped first account may require a password change.

## Verification and failure handling

Use a new browser session to verify role-appropriate login/actions and absence of administrative actions for ordinary roles. For lost passwords or disabled administrators, do not clear users, disable authentication or repeatedly bootstrap. Preserve backups and identify the active account source before recovery. [Mode conversion](account-mode-change.md) is not a password reset tool.
