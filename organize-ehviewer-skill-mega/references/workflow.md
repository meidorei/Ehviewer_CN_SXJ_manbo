# EhViewer manga organization workflow

## Contents

1. Scope and artifacts
2. Snapshot contract
3. Candidate generation
4. Three semantic-review rounds
5. Final decision contract
6. Human review page
7. Validation and dry run
8. Device replacement and recovery

## 1. Scope and artifacts

Operate on the explicitly selected Debug package and its `DOWNLOADS` table. The list order is `TIME DESC`; do not assume a separate ordering column.

Keep one artifact directory per run containing the phone snapshot, title catalog, candidate files, model decisions, global index, review page, human-exported order, original backup, sorted copy, and verification report.

## 2. Snapshot contract

Read the source database in SQLite read-only mode and require `PRAGMA integrity_check = ok`. Query metadata in `TIME DESC` order. Compute the snapshot fingerprint as SHA-256 of sorted decimal GIDs joined by newline.

Catalog schema:

```json
{
  "formatVersion": 2,
  "snapshotFingerprint": "<64 lowercase hex characters>",
  "items": [
    {
      "gid": 100001,
      "originalPosition": 1,
      "title": "<original title>",
      "titleJpn": "<optional alternate title>",
      "label": null,
      "state": 0
    }
  ]
}
```

Do not replace original titles with normalized forms. Derived author, skeleton, token, sequence, range, and part fields are additions only.

## 3. Candidate generation

Generate an overlapping union from same-author title skeletons, explicit sequence markers, nearby distinctive tokens, possible cross-script author aliases, translated titles, reverse searches around known series, and complete author buckets.

Program rules may propose candidates but may not produce semantic merge conclusions.

## 4. Three semantic-review rounds

### Round 1: candidate adjudication

Use the primary model to accept, reject, split, extend, or merge overlapping candidates. Require strict JSON without Markdown. Judge author identity and title meaning together; never merge solely because the author matches.

Recognize numbered chapters, ranges, upper/middle/lower, front/back parts, collections, extras, remasters, and parallel branches. Collections remain as downloaded items even when they overlap individual chapters.

When uncertain, keep an item independent and lower confidence.

### Round 2: author-bucket audit

Review complete author buckets for missing members, cross-script aliases, translated titles, shortened sequel titles, extras, collections, and remasters. This round is mandatory even when the first round appears complete.

### Round 3: global series-index audit

Compress all multi-item series into a global index. Review duplicate canonical IDs, aliases split across batches, contradictory ordering, cross-script merges, collection-range conflicts, remaster branches, and every series below confidence `0.85` with original titles attached.

## 5. Final decision contract

```json
{
  "formatVersion": 2,
  "snapshotFingerprint": "<same fingerprint>",
  "decisions": [
    {
      "gid": 100001,
      "canonicalSeriesId": "series:placeholder",
      "canonicalSeriesTitle": "<canonical title>",
      "branch": "main",
      "itemOrder": 1,
      "confidence": 0.95,
      "reason": "<short semantic reason>"
    }
  ],
  "seriesOrder": ["series:placeholder"],
  "gidOrder": [100001]
}
```

Anchor a series at its earliest original member. Sort reliable members by `itemOrder`. Preserve original relative order for unknown or tied positions. Preserve every download, including duplicate translations and overlapping collections.

## 6. Human review page

Write every title and GID directly into the HTML. Runtime JavaScript may enhance the page but must not be required to load the catalog.

Provide search, collapsible series, indented children, confidence and reasons, whole-series and within-series drag, low-confidence expansion, single-item pinning without moving the entire series, restoration, and complete JSON export with the unchanged fingerprint.

Pinned items change order only. They are not deletion instructions.

## 7. Validation and dry run

Reject Markdown fences, non-JSON prefix/suffix, wrong format version, fingerprint mismatch, missing/extra/duplicate GIDs, decision/order mismatch, invalid numeric fields, or changed semantic fields in a human export. Model output should keep each series contiguous; a human export may intentionally split a series when a single member is pinned for duplicate review.

Before touching the device, export a fresh database and recheck its fingerprint. Create a computer backup and sorted copy. In one `BEGIN IMMEDIATE` transaction, write unique descending `TIME` values. Require one updated row per GID, exact `TIME DESC` order, unique `TIME`, integrity `ok`, and equality of every non-`TIME` field digest.

## 8. Device replacement and recovery

Force-stop the target package. Create phone-side and computer backups and compare hashes. Upload the verified sorted database to app cache and compare SHA-256. Inspect for live WAL or hot journal files before replacement.

Replace only within the app-owned filesystem through `run-as`. Keep the phone backup. Re-export and validate immediately, then repeat after one successful Debug-package launch.

If any step fails, stop. Do not delete the original or backup, do not use root, and do not try a release package.
