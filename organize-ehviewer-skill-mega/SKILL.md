---
name: organize-ehviewer-manga
description: Organize EhViewer DOWNLOADS records into multilingual manga series using deterministic high-recall candidate generation, primary-model semantic adjudication, author-bucket and global audits, an offline human review page, strict JSON validation, SQLite dry-run writeback, backups, and verified Android Debug-package replacement. Use when asked to sort, group, review, deduplicate, or safely reorder an EhViewer manga library or its eh.db database.
---

# Organize EhViewer Manga

Run a gated workflow: export safely, generate candidates, let the primary model decide semantics, require human review, then validate and write only the confirmed order.

## Non-negotiable rules

- Work only on the package and database explicitly placed in scope. Default to `com.ehviewer.manbo.debug`; never substitute a release package.
- Use the primary agent/model for semantic decisions. Do not delegate to subagents unless the user explicitly requests parallel agents.
- Never use title similarity as an automatic merge verdict. Rules generate candidates only.
- Never send database binaries, device paths, tokens, cookies, or download paths to a model. Use GID and title metadata only.
- Preserve every GID. Do not delete suspected duplicates; the user decides deletion separately.
- Stop after generating the review page. Continue writeback only after the user returns or explicitly confirms the exported order JSON.
- Before device replacement, require matching fingerprints, a computer backup, a phone backup, a successful SQLite dry run, exact `TIME DESC` order, and `PRAGMA integrity_check = ok`.
- Stop on any permission, fingerprint, count, uniqueness, order, integrity, or hash mismatch. Never fall back to root.

## Read the detailed workflow

Read [references/workflow.md](references/workflow.md) before starting a new library run. It defines the three model-review rounds, JSON contracts, review-page requirements, safety gates, and recovery behavior.

Read [references/prompts.md](references/prompts.md) immediately before the three semantic-review rounds. Use its schemas and constraints, replacing placeholders only with the current run's safe metadata.

## Workflow

1. Confirm the ADB executable, device serial, target package, and an empty artifact directory.
2. Force-stop the target package and export `databases/eh.db` with `scripts/adb_transfer.py export`. This script uses `run-as ... cat` so binary output is not contaminated by transfer statistics.
3. Run `scripts/snapshot_catalog.py` to check SQLite integrity and create `llm-full-catalog.json` plus `snapshot-summary.json`.
4. Run `scripts/generate_candidates.py` to create overlapping high-recall candidates and complete author buckets.
5. Perform three primary-model rounds: candidate adjudication, author-bucket missing-member audit, and global series-index conflict audit.
6. Merge decisions into one full version-2 decision file, then run `scripts/build_order.py`. Keep each series block at its earliest original member, preserve unrelated blocks' relative order, and display reliable members from newest to oldest by descending semantic `itemOrder`. Preserve original relative order for tied or unknown member positions.
7. Run `scripts/validate_order.py` against the model JSON.
8. Generate a self-contained offline page with `scripts/generate_review_page.py`. Confirm that every title and GID is present statically in the HTML.
9. Hand the page to the user and stop. Do not touch the device database while awaiting human review.
10. When the user returns the exported JSON, run `scripts/validate_order.py` again, now with `--model` to ensure semantic fields were not silently modified.
11. Run `scripts/apply_order.py` against a newly exported phone database. It creates a backup and sorted copy, modifies only `TIME`, and rejects any non-`TIME` change.
12. Create and hash a phone-side backup, upload the sorted copy to app cache with `scripts/adb_transfer.py upload`, then perform the same-filesystem replacement with `run-as` only.
13. Re-export before launch and after one successful app launch. Validate both exports against the confirmed JSON and retain all backups.

## Bundled scripts

- `snapshot_catalog.py`: read-only SQLite inspection, catalog export, GID fingerprint, and database hash.
- `generate_candidates.py`: deterministic high-recall candidates and full author buckets.
- `build_order.py`: turn one full set of semantic decisions into stable `seriesOrder` and `gidOrder` arrays while keeping original series anchors and showing members newest-first.
- `validate_order.py`: strict JSON, fingerprint, GID, numeric-field, series-contiguity, and optional semantic-field validation.
- `generate_review_page.py`: static offline review page with search, collapsible series, series/internal drag, single-item pinning, restore, and JSON export.
- `apply_order.py`: backup plus transactional dry-run rewrite of `DOWNLOADS.TIME`; verifies exact order, integrity, uniqueness, and preservation of all other fields.
- `adb_transfer.py`: exact `run-as` export and hash-checked upload to app cache. It intentionally does not perform replacement.

Run every script with `--help` before first use. Keep all outputs in one per-run artifact directory, never inside the Android repository unless the user explicitly asks.

## Human-review behavior

Treat pinned items as reordered items, not deletions. Export a complete GID order even when the user plans to delete duplicates later. If the user deletes records on the phone, export a new snapshot before any further writeback because the previous fingerprint is invalid.

## Completion report

Report the item count, series count, low-confidence series count, snapshot fingerprint, final JSON hash, backup locations and hashes, dry-run status, pre-launch verification, post-launch verification, and any limitation in visual inspection.
