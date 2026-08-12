# Primary-model prompt contracts

Use only safe metadata from the current catalog. Do not include filesystem paths, database bytes, cookies, tokens, or unrelated device information. Require strict JSON without explanatory prose or Markdown fences.

## Candidate adjudication

Instruct the primary model to act as a multilingual manga cataloger and to treat candidates as high-recall proposals that may over-group, under-group, or overlap.

Require it to:

1. Assign a canonical series ID and title to every supplied GID.
2. Merge cross-script author aliases only when title meaning also supports the relationship.
3. Recognize chapters, episodes, volumes, parts, ranges, upper/middle/lower, front/back, collections, extras, remasters, and branches.
4. Keep overlapping collection and single-chapter downloads as separate records.
5. Preserve original relative order when reliable series order is unavailable.
6. Reject false candidates and keep uncertain items independent.
7. Return confidence in `[0,1]` and a short semantic reason.

Response schema:

```json
{
  "formatVersion": 2,
  "candidateId": "<input candidate id>",
  "decisions": [
    {
      "gid": 100001,
      "canonicalSeriesId": "series:placeholder",
      "canonicalSeriesTitle": "<canonical title>",
      "branch": "main",
      "itemOrder": 1,
      "confidence": 0.95,
      "reason": "<short reason>"
    }
  ],
  "possibleExternalAliases": []
}
```

## Author-bucket missing-member audit

Provide full author buckets plus already accepted series memberships. Ask the primary model to return only supported additions, splits, alias links, and rejected false positives. Emphasize translated titles, cross-script author names, shortened sequel titles, extras, collections, and remasters. Do not allow author identity alone to create a series.

## Global series-index audit

Provide compressed multi-item series cards. Include original titles for all low-confidence, cross-script, collection-range, extra, and remaster cases. Ask the primary model to detect duplicate canonical IDs, aliases split between batches, conflicting branch placement, missing members, and contradictory order.

## Full-decision handoff

After resolving all three rounds, create one decision object for every catalog GID. Use a unique `item:<gid>` canonical ID for independent items. Do not omit rejected candidates; represent them as independent decisions. Pass the full decision file through `build_order.py` and `validate_order.py` before generating the human review page.
