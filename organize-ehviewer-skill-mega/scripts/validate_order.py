#!/usr/bin/env python3
import argparse
import hashlib
import json
from pathlib import Path


SEMANTIC_FIELDS = (
    "canonicalSeriesId",
    "canonicalSeriesTitle",
    "branch",
    "itemOrder",
    "confidence",
    "reason",
)


def strict_json(path):
    raw = path.read_text(encoding="utf-8").strip()
    if raw.startswith("```") or raw.endswith("```"):
        raise ValueError(f"{path} contains a Markdown fence")
    value, end = json.JSONDecoder().raw_decode(raw)
    if raw[end:].strip():
        raise ValueError(f"{path} contains non-JSON suffix text")
    return value


def main():
    parser = argparse.ArgumentParser(description="Strictly validate an EhViewer series-order JSON file.")
    parser.add_argument("--catalog", required=True, type=Path)
    parser.add_argument("--order", required=True, type=Path)
    parser.add_argument("--model", type=Path, help="Optional original model JSON for semantic-field comparison.")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    catalog = strict_json(args.catalog)
    order = strict_json(args.order)
    errors = []

    if order.get("formatVersion") != 2:
        errors.append("formatVersion must equal 2")
    fingerprint = catalog.get("snapshotFingerprint")
    if order.get("snapshotFingerprint") != fingerprint:
        errors.append("snapshot fingerprint mismatch")
    catalog_gids = [int(item["gid"]) for item in catalog.get("items", [])]
    gid_order = [int(gid) for gid in order.get("gidOrder", [])]
    decisions = order.get("decisions", [])
    decision_gids = [int(row.get("gid")) for row in decisions]
    if len(gid_order) != len(catalog_gids):
        errors.append("gidOrder length mismatch")
    if len(gid_order) != len(set(gid_order)):
        errors.append("gidOrder contains duplicates")
    if set(gid_order) != set(catalog_gids):
        errors.append("gidOrder contains missing or extra GIDs")
    if decision_gids != gid_order:
        errors.append("decisions are not aligned with gidOrder")
    recomputed = hashlib.sha256(
        "\n".join(str(gid) for gid in sorted(gid_order)).encode("utf-8")
    ).hexdigest()
    if recomputed != fingerprint:
        errors.append("gidOrder fingerprint mismatch")

    for row in decisions:
        gid = row.get("gid")
        item_order = row.get("itemOrder")
        confidence = row.get("confidence")
        if isinstance(item_order, bool) or not isinstance(item_order, (int, float)):
            errors.append(f"GID {gid}: itemOrder is not numeric")
        if isinstance(confidence, bool) or not isinstance(confidence, (int, float)) or not 0 <= confidence <= 1:
            errors.append(f"GID {gid}: confidence is outside 0..1")
        for field in ("canonicalSeriesId", "canonicalSeriesTitle", "reason"):
            if not row.get(field):
                errors.append(f"GID {gid}: missing {field}")

    series_order = order.get("seriesOrder", [])
    decision_series = {row.get("canonicalSeriesId") for row in decisions}
    if len(series_order) != len(set(series_order)):
        errors.append("seriesOrder contains duplicates")
    if set(series_order) != decision_series:
        errors.append("seriesOrder set mismatch")
    first_series = []
    seen = set()
    by_gid = {int(row["gid"]): row for row in decisions}
    for gid in gid_order:
        series_id = by_gid[gid]["canonicalSeriesId"]
        if series_id not in seen:
            seen.add(series_id)
            first_series.append(series_id)
    if first_series != series_order:
        errors.append("seriesOrder is not the series first-occurrence order")

    if args.model:
        model = strict_json(args.model)
        if model.get("snapshotFingerprint") != fingerprint:
            errors.append("model fingerprint mismatch")
        model_by_gid = {int(row["gid"]): row for row in model.get("decisions", [])}
        for row in decisions:
            reference = model_by_gid.get(int(row["gid"]))
            if reference is None:
                errors.append(f"GID {row['gid']}: absent from model")
                continue
            for field in SEMANTIC_FIELDS:
                if row.get(field) != reference.get(field):
                    errors.append(f"GID {row['gid']}: semantic field changed: {field}")

    result = {
        "status": "passed" if not errors else "failed",
        "errors": errors,
        "itemCount": len(gid_order),
        "seriesCount": len(series_order),
        "snapshotFingerprint": fingerprint,
    }
    if args.report:
        args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if errors:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
