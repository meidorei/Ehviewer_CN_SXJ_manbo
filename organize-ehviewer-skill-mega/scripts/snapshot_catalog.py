#!/usr/bin/env python3
import argparse
import hashlib
import json
import sqlite3
from pathlib import Path


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description="Create a safe title catalog from an EhViewer database snapshot.")
    parser.add_argument("--db", required=True, type=Path)
    parser.add_argument("--catalog", required=True, type=Path)
    parser.add_argument("--summary", required=True, type=Path)
    args = parser.parse_args()
    source = args.db.resolve()
    if not source.is_file():
        raise FileNotFoundError(source)
    for output in (args.catalog, args.summary):
        if output.exists():
            raise FileExistsError(f"refusing to overwrite {output}")

    db = sqlite3.connect(f"file:{source}?mode=ro", uri=True)
    try:
        integrity = db.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            raise RuntimeError(f"integrity_check failed: {integrity}")
        required = {"GID", "TITLE", "TITLE_JPN", "LABEL", "STATE", "TIME"}
        columns = {row[1].upper() for row in db.execute("PRAGMA table_info(DOWNLOADS)")}
        missing = required - columns
        if missing:
            raise RuntimeError(f"DOWNLOADS is missing columns: {sorted(missing)}")
        rows = db.execute(
            "SELECT GID,TITLE,TITLE_JPN,LABEL,STATE,TIME FROM DOWNLOADS ORDER BY TIME DESC"
        ).fetchall()
        gids = [int(row[0]) for row in rows]
        if len(gids) != len(set(gids)):
            raise RuntimeError("DOWNLOADS contains duplicate GIDs")
        fingerprint = hashlib.sha256(
            "\n".join(str(gid) for gid in sorted(gids)).encode("utf-8")
        ).hexdigest()
        items = [
            {
                "gid": int(gid),
                "originalPosition": position,
                "title": title,
                "titleJpn": title_jpn,
                "label": label,
                "state": state,
            }
            for position, (gid, title, title_jpn, label, state, _time) in enumerate(rows, 1)
        ]
        time_range = list(db.execute("SELECT MIN(TIME),MAX(TIME) FROM DOWNLOADS").fetchone())
        time_distinct = db.execute("SELECT COUNT(DISTINCT TIME) FROM DOWNLOADS").fetchone()[0]
    finally:
        db.close()

    catalog = {"formatVersion": 2, "snapshotFingerprint": fingerprint, "items": items}
    summary = {
        "formatVersion": 2,
        "databaseFile": source.name,
        "databaseSha256": sha256_file(source),
        "itemCount": len(items),
        "snapshotFingerprint": fingerprint,
        "integrityCheck": integrity,
        "timeDistinctCount": time_distinct,
        "timeRange": time_range,
    }
    args.catalog.parent.mkdir(parents=True, exist_ok=True)
    args.summary.parent.mkdir(parents=True, exist_ok=True)
    args.catalog.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    args.summary.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
