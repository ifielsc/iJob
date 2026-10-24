# Maintain bilingual docs, catalogs and screenshots

[简体中文](../../zh-CN/development/documentation.md) · [Manual home](../README.md)

## Keep both languages aligned

Use identical relative filenames, synchronizing facts, commands, keys, enums and samples page by page. Link the opposite language and manual home at the top. Explain fields in page/plugin references; scenarios link there and provide inputs/results instead of duplicating rules.

Shared configuration facts live in `document/reference/configuration-review.json`; the setup checker's 54 entries come from `user-settings.tsv`. New settings need sources/defaults/levels, format validation and bilingual explanations. After table edits:

```sh
python tools/distribution/docs.py --write
python tools/distribution/docs.py --check
```

The checker verifies recursive language pairing, switches, local links, image files and generated tables. It does not replace editorial review, real UI inspection or external-system acceptance.

## Real screenshots

Register isolated resources/synthetic data before running scenarios. Switch each real page to its target language and capture in light theme at 1440×1000 after forms/editors/graphs load. Exclude secrets/full private configuration and never fabricate successful states. A shared screenshot index records source version, page, language, hashes and verification category; runtime logs/credentials remain outside the repository.

Place images under each language's images/manual with alt text, captions and copyable configuration. Screenshot IDs/paths belong to the rehearsal; prose uses replaceable placeholders. Read-only previews are not delivery/execution evidence.
