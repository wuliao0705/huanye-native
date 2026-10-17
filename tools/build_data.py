#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把交接包的 cats_cards.json + details.json 压成 APK 用的精简格式。
原始 87MB → 预计 ~25MB（去重复字段 + 详情截断 + 字段重命名）
@author 无聊
"""
import json, os, sys, io

SRC = sys.argv[1] if len(sys.argv) > 1 else "/tmp/hy_pkg"
OUT = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets"

os.makedirs(OUT, exist_ok=True)

# ---------- 卡表 ----------
print("[1/3] 读 cats_cards.json …")
raw = json.load(open(os.path.join(SRC, "data/cats_cards.json"), encoding="utf-8"))
idx = raw.get("idx", {})
print("  桶数:", len(idx))

# 用数组压缩：每个源一张表，卡片用 [id, name, cover, summary, tags, cat]
srcs = {}
total = 0
for bucket, cards in idx.items():
    s, _, cat = bucket.partition("|")
    srcs.setdefault(s, {})
    srcs[s].setdefault(cat, [])
    for c in cards:
        cid = c.get("id") or ""
        if not cid:
            continue
        srcs[s][cat].append([
            cid,
            c.get("name", ""),
            c.get("cover", ""),
            c.get("summary", ""),
            c.get("tags") or [],
        ])
        total += 1
print("  卡片:", total)

# 去掉每源重复的 id（同一卡可能挂多个分类）
for s, cats in srcs.items():
    seen = set()
    for cat, arr in cats.items():
        out = []
        for row in arr:
            if row[0] in seen:
                continue
            seen.add(row[0])
            out.append(row)
        cats[cat] = out

# ---------- 详情 ----------
print("[2/3] 读 details.json …")
det = {}
dp = os.path.join(SRC, "data/details.json")
if os.path.exists(dp):
    draw = json.load(open(dp, encoding="utf-8"))
    print("  详情:", len(draw))
    for k, v in draw.items():
        # 截断：开场白 1200、设定 800、简介 600、作者 40
        det[k] = [
            (v.get("firstMessage") or v.get("o") or "")[:1200],
            (v.get("scenario") or v.get("s") or "")[:800],
            (v.get("intro") or v.get("g") or "")[:600],
            (v.get("author") or v.get("cr") or "")[:40],
        ]

# ---------- 写出 ----------
print("[3/3] 写出 …")

def dump(obj, name):
    p = os.path.join(OUT, name)
    with io.open(p, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))
    print("  %-18s %8.2f MB" % (name, os.path.getsize(p) / 1048576))
    return p

dump({"v": 7, "srcs": srcs, "total": total}, "cards.json")
dump(det, "details.json")

sz = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
print("合计 %.2f MB" % (sz / 1048576))
