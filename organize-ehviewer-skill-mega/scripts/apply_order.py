#!/usr/bin/env python3
import argparse
import hashlib
import json
import shutil
import sqlite3
from pathlib import Path


BASE_TIME = 9_000_000_000_000_000


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def value_bytes(value):
    if value is None:
        return b"N;"
    if isinstance(value, bytes):
        return b"B" + str(len(value)).encode() + b":" + value + b";"
    encoded = str(value).encode("utf-8")
    return type(value).__name__.encode() + b":" + str(len(encoded)).encode() + b":" + encoded + b";"


def inspect(path):
    db = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    try:
        integrity = db.execute("PRAGMA integrity_check").fetchone()[0]
        columns = [row[1] for row in db.execute("PRAGMA table_info(DOWNLOADS)") if row[1].upper() != "TIME"]
        quoted = ",".join('"' + column.replace('"', '""') + '"' for column in columns)
        digest = hashlib.sha256()
        for row in db.execute(f"SELECT {quoted} FROM DOWNLOADS ORDER BY GID"):
            for value in row:
                digest.update(value_bytes(value))
            digest.update(b"\n")
        gids = [int(row[0]) for row in db.execute("SELECT GID FROM DOWNLOADS")]
        fingerprint = hashlib.sha256("\n".join(str(gid) for gid in sorted(gids)).encode()).hexdigest()
        return integrity, gids, fingerprint, digest.hexdigest(), columns
    finally:
        db.close()


def main():
    parser = argparse.ArgumentParser(description="Create and verify a sorted EhViewer database copy.")
    parser.add_argument("--db", required=True, type=Path)
    parser.add_argument("--order", required=True, type=Path)
    parser.add_argument("--backup", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    args = parser.parse_args()
    source = args.db.resolve()
    for target in (args.backup, args.output, args.report):
        if target.exists():
            raise FileExistsError(f"refusing to overwrite {target}")
    order = json.loads(args.order.read_text(encoding="utf-8"))
    gid_order = [int(gid) for gid in order["gidOrder"]]
    integrity, source_gids, fingerprint, source_digest, columns = inspect(source)
    if integrity != "ok":
        raise RuntimeError(f"source integrity_check failed: {integrity}")
    if fingerprint != order.get("snapshotFingerprint") or set(source_gids) != set(gid_order):
        raise RuntimeError("source database does not match the confirmed order snapshot")

    shutil.copy2(source, args.backup)
    shutil.copy2(source, args.output)
    if sha256_file(source) != sha256_file(args.backup):
        raise RuntimeError("backup hash mismatch")
    db = sqlite3.connect(args.output)
    try:
        db.execute("PRAGMA journal_mode=DELETE")
        db.execute("BEGIN IMMEDIATE")
        for index, gid in enumerate(gid_order):
            cursor = db.execute("UPDATE DOWNLOADS SET TIME=? WHERE GID=?", (BASE_TIME - index, gid))
            if cursor.rowcount != 1:
                raise RuntimeError(f"GID {gid} updated {cursor.rowcount} rows")
        db.commit()
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()

    result_integrity, _gids, result_fingerprint, result_digest, _columns = inspect(args.output)
    verify = sqlite3.connect(f"file:{args.output}?mode=ro", uri=True)
    try:
        actual = [int(row[0]) for row in verify.execute("SELECT GID FROM DOWNLOADS ORDER BY TIME DESC")]
        distinct_times = verify.execute("SELECT COUNT(DISTINCT TIME) FROM DOWNLOADS").fetchone()[0]
        time_range = list(verify.execute("SELECT MIN(TIME),MAX(TIME) FROM DOWNLOADS").fetchone())
    finally:
        verify.close()
    if result_integrity != "ok" or actual != gid_order or distinct_times != len(gid_order):
        raise RuntimeError("sorted copy failed integrity, order, or unique-TIME validation")
    if result_fingerprint != fingerprint or result_digest != source_digest:
        raise RuntimeError("sorted copy changed GIDs or non-TIME fields")
    report = {
        "status": "passed",
        "itemCount": len(gid_order),
        "snapshotFingerprint": fingerprint,
        "sourceSha256": sha256_file(source),
        "backupSha256": sha256_file(args.backup),
        "sortedSha256": sha256_file(args.output),
        "integrityCheck": result_integrity,
        "orderMatches": True,
        "distinctTimeCount": distinct_times,
        "timeRange": time_range,
        "nonTimeColumnsPreserved": columns,
    }
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
