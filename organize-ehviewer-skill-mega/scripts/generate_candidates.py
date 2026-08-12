#!/usr/bin/env python3
import argparse
import json
import re
import unicodedata
from collections import defaultdict
from difflib import SequenceMatcher
from pathlib import Path


BRACKET = re.compile(r"\[([^\]]+)\]")
PAREN = re.compile(r"\(([^)]*)\)")
NON_WORD = re.compile(r"[^0-9a-z\u3040-\u30ff\u3400-\u9fff]+")
SEQUENCE = re.compile(r"(?ix)(?:\b(?:ch(?:apter)?|ep(?:isode)?|vol(?:ume)?|part)\.?\s*[-#:]*\s*)(\d+(?:\.\d+)?)|第\s*(\d+(?:\.\d+)?)\s*(?:話|话|章|巻|卷|部|篇|集)")
RANGE = re.compile(r"(?<!\d)(\d{1,3})\s*[-~～至]\s*(\d{1,3})(?!\d)")
PART = re.compile(r"(?i)\b(?:zenpen|chuuhen|kouhen|first|middle|final|extra|remake|remaster)\b|前[編篇]|中[編篇]|後[編篇]|后[篇编]|上[巻卷篇]?|中[巻卷篇]?|下[巻卷篇]?|番外|外[伝传]|重[制製]|[总総]集[篇編]|合集")
LANGUAGE = {"chinese", "english", "japanese", "digital", "decensored", "uncensored", "translated", "sample", "中文", "汉化", "漢化", "中国翻訳", "中國翻譯", "無修正", "无修正"}


def fold(text):
    return unicodedata.normalize("NFKC", text or "").casefold()


def normalize_piece(text):
    return NON_WORD.sub("", re.sub(r"\([^)]*\)", " ", fold(text)))


def authors(title):
    match = BRACKET.match((title or "").strip())
    if not match or fold(match.group(1)).strip() in LANGUAGE:
        return []
    raw = match.group(1)
    values = [raw, *re.split(r"[/,&、，]", raw)]
    paren = PAREN.search(raw)
    if paren:
        values.extend(re.split(r"[/,&、，]", paren.group(1)))
        values.append(raw[:paren.start()])
    result = []
    for value in values:
        normalized = normalize_piece(value)
        if len(normalized) >= 2 and normalized not in result:
            result.append(normalized)
    return result


def skeleton(title):
    text = fold(title)
    text = BRACKET.sub(" ", text)
    text = PAREN.sub(" ", text)
    text = SEQUENCE.sub(" ", text)
    text = RANGE.sub(" ", text)
    text = PART.sub(" ", text)
    return NON_WORD.sub("", text)


def tokens(title):
    text = fold(title)
    text = BRACKET.sub(" ", text)
    text = PAREN.sub(" ", text)
    text = SEQUENCE.sub(" ", text)
    text = RANGE.sub(" ", text)
    text = PART.sub(" ", text)
    return {token for token in NON_WORD.sub(" ", text).split() if len(token) >= 3}


class UnionFind:
    def __init__(self, values):
        self.parent = {value: value for value in values}

    def find(self, value):
        while self.parent[value] != value:
            self.parent[value] = self.parent[self.parent[value]]
            value = self.parent[value]
        return value

    def union(self, left, right):
        left, right = self.find(left), self.find(right)
        if left != right:
            self.parent[right] = left


def main():
    parser = argparse.ArgumentParser(description="Generate overlapping high-recall manga-series candidates and author buckets.")
    parser.add_argument("--catalog", required=True, type=Path)
    parser.add_argument("--candidates", required=True, type=Path)
    parser.add_argument("--author-buckets", required=True, type=Path)
    args = parser.parse_args()
    for output in (args.candidates, args.author_buckets):
        if output.exists():
            raise FileExistsError(f"refusing to overwrite {output}")
    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    items = catalog["items"]
    by_gid = {int(item["gid"]): item for item in items}
    derived = {}
    author_buckets = defaultdict(list)
    skeleton_buckets = defaultdict(list)
    for item in items:
        gid = int(item["gid"])
        item_authors = []
        item_skeletons = []
        for title in (item.get("title"), item.get("titleJpn")):
            for author in authors(title):
                if author not in item_authors:
                    item_authors.append(author)
            title_skeleton = skeleton(title)
            if len(title_skeleton) >= 4 and title_skeleton not in item_skeletons:
                item_skeletons.append(title_skeleton)
        sequence_signal = any(SEQUENCE.search(title or "") or RANGE.search(title or "") or PART.search(title or "") for title in (item.get("title"), item.get("titleJpn")))
        item_tokens = set()
        for title in (item.get("title"), item.get("titleJpn")):
            item_tokens.update(tokens(title))
        derived[gid] = {"authors": item_authors, "skeletons": item_skeletons, "sequenceSignal": sequence_signal, "tokens": sorted(item_tokens)}
        for author in item_authors:
            author_buckets[author].append(gid)
        for title_skeleton in item_skeletons:
            skeleton_buckets[title_skeleton].append(gid)

    union = UnionFind(by_gid)
    reasons = defaultdict(set)
    for gids in skeleton_buckets.values():
        if 2 <= len(gids) <= 40:
            for gid in gids[1:]:
                union.union(gids[0], gid)
                reasons[tuple(sorted((gids[0], gid)))].add("same-title-skeleton")
    for bucket in author_buckets.values():
        gids = list(dict.fromkeys(bucket))
        if not 2 <= len(gids) <= 120:
            continue
        for index, left in enumerate(gids):
            for right in gids[index + 1:]:
                ratio = max((SequenceMatcher(None, a, b).ratio() for a in derived[left]["skeletons"] for b in derived[right]["skeletons"]), default=0)
                sequence_signal = derived[left]["sequenceSignal"] or derived[right]["sequenceSignal"]
                if ratio >= 0.86 or (ratio >= 0.68 and sequence_signal):
                    union.union(left, right)
                    reasons[tuple(sorted((left, right)))].add("same-author-fuzzy-title")
                    if sequence_signal:
                        reasons[tuple(sorted((left, right)))].add("numeric-or-part-sequence")

    # Nearby rows with a distinctive shared token are a deliberately weak recall channel.
    for index, left_item in enumerate(items):
        left = int(left_item["gid"])
        left_tokens = set(derived[left]["tokens"])
        for right_item in items[index + 1:index + 13]:
            right = int(right_item["gid"])
            shared = left_tokens.intersection(derived[right]["tokens"])
            if not any(len(token) >= 6 for token in shared):
                continue
            ratio = max((SequenceMatcher(None, a, b).ratio() for a in derived[left]["skeletons"] for b in derived[right]["skeletons"]), default=0)
            if ratio >= 0.58:
                union.union(left, right)
                reasons[tuple(sorted((left, right)))].add("nearby-shared-title-token")

    components = defaultdict(list)
    for gid in by_gid:
        components[union.find(gid)].append(gid)
    candidate_rows = []
    for gids in components.values():
        if len(gids) < 2:
            continue
        gids.sort(key=lambda gid: by_gid[gid]["originalPosition"])
        gid_set = set(gids)
        recall = sorted({reason for pair, pair_reasons in reasons.items() if set(pair) <= gid_set for reason in pair_reasons})
        candidate_rows.append({"candidateId": "pending", "recallReasons": recall, "items": [{**by_gid[gid], "derived": derived[gid]} for gid in gids]})
    candidate_rows.sort(key=lambda row: row["items"][0]["originalPosition"])
    for index, row in enumerate(candidate_rows, 1):
        row["candidateId"] = f"candidate:{index:05d}"
    audits = []
    seen_sets = set()
    for author, gids in author_buckets.items():
        unique = tuple(sorted(set(gids), key=lambda gid: by_gid[gid]["originalPosition"]))
        if len(unique) >= 2 and unique not in seen_sets:
            seen_sets.add(unique)
            audits.append({"authorKey": author, "itemCount": len(unique), "items": [by_gid[gid] for gid in unique]})
    audits.sort(key=lambda row: (-row["itemCount"], row["authorKey"]))
    candidate_output = {"formatVersion": 2, "snapshotFingerprint": catalog["snapshotFingerprint"], "candidateCount": len(candidate_rows), "candidates": candidate_rows}
    author_output = {"formatVersion": 2, "snapshotFingerprint": catalog["snapshotFingerprint"], "authorBucketCount": len(audits), "buckets": audits}
    args.candidates.write_text(json.dumps(candidate_output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    args.author_buckets.write_text(json.dumps(author_output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"candidateCount": len(candidate_rows), "authorBucketCount": len(audits)}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
