#!/usr/bin/env python3
import argparse
import html
import json
from collections import defaultdict
from pathlib import Path


def esc(value):
    return html.escape(str(value), quote=True)


def item_html(item, decision, child):
    title = item.get("title") or item.get("titleJpn") or f"GID {item['gid']}"
    alternate = item.get("titleJpn")
    alternate_html = f'<div class="alternate">{esc(alternate)}</div>' if alternate and alternate != title else ""
    search = " ".join(
        str(value or "")
        for value in (item["gid"], title, alternate, decision["canonicalSeriesId"], decision["canonicalSeriesTitle"])
    ).casefold()
    classes = "item child" if child else "item standalone"
    chips = [f"GID {item['gid']}", f"原位置 {item['originalPosition']}"]
    if child:
        chips.extend((f"分支 {decision.get('branch', 'main')}", f"顺序 {decision.get('itemOrder', 0)}"))
    return f'''<article class="{classes}" data-gid="{item['gid']}" data-series-id="{esc(decision['canonicalSeriesId'])}" data-search="{esc(search)}" draggable="{'true' if child else 'false'}">
      <div class="item-body"><span class="item-grip">⋮⋮</span><div><div class="title">{esc(title)}</div>{alternate_html}<div class="chips">{' · '.join(esc(chip) for chip in chips)}</div></div></div>
      <button class="pin" type="button">单本置顶</button>
    </article>'''


def main():
    parser = argparse.ArgumentParser(description="Generate a self-contained offline EhViewer manga review page.")
    parser.add_argument("--catalog", required=True, type=Path)
    parser.add_argument("--order", required=True, type=Path, help="Validated full model decision JSON.")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(f"refusing to overwrite {args.output}")
    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    order = json.loads(args.order.read_text(encoding="utf-8"))
    if catalog["snapshotFingerprint"] != order["snapshotFingerprint"]:
        raise RuntimeError("catalog/order fingerprint mismatch")
    items = {int(item["gid"]): item for item in catalog["items"]}
    decisions = {int(row["gid"]): row for row in order["decisions"]}
    grouped = defaultdict(list)
    for gid in order["gidOrder"]:
        grouped[decisions[int(gid)]["canonicalSeriesId"]].append(int(gid))
    if set(grouped) != set(order["seriesOrder"]):
        raise RuntimeError("seriesOrder set mismatch")

    cards = []
    multi = low = singletons = 0
    for series_id in order["seriesOrder"]:
        gids = grouped[series_id]
        rows = [decisions[gid] for gid in gids]
        source_items = [items[gid] for gid in gids]
        title = rows[0]["canonicalSeriesTitle"]
        search = " ".join([series_id, title] + [str(item.get("title") or "") for item in source_items] + [str(item.get("titleJpn") or "") for item in source_items]).casefold()
        if len(gids) == 1:
            singletons += 1
            cards.append(f'<section class="group singleton-group" data-series-id="{esc(series_id)}" data-search="{esc(search)}" draggable="true"><span class="group-grip">⠿</span>{item_html(source_items[0], rows[0], False)}</section>')
            continue
        multi += 1
        confidence = min(float(row["confidence"]) for row in rows)
        is_low = confidence < 0.85
        low += int(is_low)
        reasons = "；".join(dict.fromkeys(row["reason"] for row in rows))
        children = "\n".join(item_html(item, row, True) for item, row in zip(source_items, rows))
        cards.append(f'''<details class="group series{' low' if is_low else ''}" data-series-id="{esc(series_id)}" data-search="{esc(search)}" draggable="true"{' open' if is_low else ''}>
          <summary><span class="group-grip">⠿</span><span><b class="series-title">{esc(title)}</b><span class="meta"><strong>{'LOW' if is_low else 'MODEL'}</strong> · {len(gids)} 本 · 置信度 {confidence:.2f}</span><span class="reason">{esc(reasons)}</span></span></summary>
          <div class="members">{children}</div>
        </details>''')

    safe_order = json.dumps(order, ensure_ascii=False, separators=(",", ":")).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")
    page = f'''<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>EhViewer 漫画系列校对台</title>
<style>
:root{{--paper:#f2eee5;--panel:#fffdf8;--soft:#faf7f0;--ink:#25221f;--muted:#746e64;--line:#d4ccbe;--accent:#b54c35;--accent-soft:#f4ddd5;--green:#52735c}}*{{box-sizing:border-box}}body{{margin:0;background:var(--paper);color:var(--ink);font:14px/1.5 "Microsoft YaHei",system-ui,sans-serif}}header{{position:sticky;top:0;z-index:10;padding:18px max(20px,calc((100vw - 1400px)/2));border-bottom:1px solid var(--line);background:#f2eee5ee;backdrop-filter:blur(12px)}}h1{{margin:0;font:700 27px Georgia,"Noto Serif SC",serif;letter-spacing:.04em}}.lead{{margin:4px 0 13px;color:var(--muted)}}.controls{{display:flex;gap:8px;flex-wrap:wrap}}button,input{{font:inherit}}input{{width:min(360px,100%);padding:9px 11px;border:1px solid var(--line);border-radius:5px;background:var(--panel)}}button{{padding:9px 12px;border:1px solid var(--ink);border-radius:5px;background:var(--panel);cursor:pointer}}button.primary{{color:white;border-color:var(--accent);background:var(--accent)}}.stats{{display:grid;grid-template-columns:repeat(5,1fr);gap:9px;margin-top:12px}}.stat{{border:1px solid var(--line);padding:8px 12px;background:var(--panel);color:var(--muted)}}.stat b{{display:block;color:var(--accent);font:700 23px Georgia,serif}}main{{max-width:1400px;margin:auto;padding:17px 20px 70px}}.note{{color:var(--muted)}}#pinned{{margin-bottom:14px;border:1px solid #c98670;background:#fff9f5}}#pinned[hidden]{{display:none}}.pinned-head{{padding:9px 13px;border-bottom:1px solid #d9aa9a;background:var(--accent-soft);color:#74301f}}#groups{{border:1px solid var(--line);background:var(--panel)}}.group{{border-bottom:1px solid var(--line)}}.group:last-child{{border-bottom:0}}.series{{background:var(--soft)}}summary{{display:grid;grid-template-columns:25px 1fr;gap:9px;padding:11px 13px;cursor:pointer;list-style:none}}summary::-webkit-details-marker{{display:none}}.series-title{{display:block}}.meta,.reason{{display:block;color:var(--muted);font-size:12px}}.meta strong{{color:var(--accent);font:700 10px ui-monospace,monospace;letter-spacing:.08em}}.members{{margin:0 13px 11px 37px;border:1px solid #d9d1c3;border-left:4px solid #c9836b;background:#f7f3eb}}.singleton-group{{display:grid;grid-template-columns:25px 1fr;align-items:stretch;padding-left:12px}}.group-grip{{align-self:center;color:#988d80;cursor:grab;font-size:18px}}.item{{display:grid;grid-template-columns:1fr 90px;border-top:1px solid var(--line);min-height:60px}}.item:first-child{{border-top:0}}.item-body{{display:flex;gap:8px;padding:10px 12px;min-width:0}}.item-grip{{color:#9d9285;cursor:grab}}.title{{font-weight:650;word-break:break-word}}.alternate,.chips{{color:var(--muted);font-size:11px;word-break:break-word}}.pin{{border:0;border-left:1px solid var(--line);border-radius:0;color:var(--accent);font-weight:700}}.pin:hover{{background:var(--accent-soft)}}#pinned-items .item{{background:linear-gradient(90deg,#f2d7cc 0 6px,#fffaf6 6px)}}#pinned-items .pin{{color:var(--green)}}.dragging{{opacity:.4}}.drop-before{{box-shadow:inset 0 4px var(--accent)}}.item-drop{{box-shadow:inset 0 3px var(--green)}}.hidden,.empty-group{{display:none!important}}#empty{{padding:40px;text-align:center;color:var(--muted)}}@media(max-width:800px){{.stats{{grid-template-columns:repeat(2,1fr)}}header{{padding:13px 10px}}main{{padding:12px 8px 60px}}.members{{margin-left:18px}}.reason{{display:none}}.item{{grid-template-columns:1fr 70px}}}}
</style></head><body><header><h1>漫画系列校对台</h1><p class="lead">离线静态清单 · 单本置顶后集中检查重复漫画</p><div class="controls"><input id="search" type="search" placeholder="搜索标题、系列名或 GID"><button id="expand">展开系列</button><button id="collapse">收起系列</button><button id="reset">恢复页面顺序</button><button id="export" class="primary">导出校对结果 JSON</button></div><div class="stats"><div class="stat"><b>{len(items)}</b>全部下载</div><div class="stat"><b>{multi}</b>模型系列</div><div class="stat"><b>{singletons}</b>独立条目</div><div class="stat"><b>{low}</b>低置信系列</div><div class="stat"><b id="visible">{len(items)}</b>当前显示</div></div></header>
<main><p class="note"><b>单本置顶</b>只移动该漫画，不移动整个系列；置顶后可点击“归位”。删除重复项应在排序确认之后另行执行。</p><section id="pinned" hidden><div class="pinned-head"><b>待处理漫画</b> · <span id="pinned-count">0</span> 本</div><div id="pinned-items"></div></section><div id="groups">{''.join(cards)}</div><div id="empty" hidden>没有匹配的漫画</div></main>
<script id="order-data" type="application/json">{safe_order}</script><script>
(()=>{{'use strict';const root=document.getElementById('groups'),pinned=document.getElementById('pinned'),pinnedItems=document.getElementById('pinned-items'),data=JSON.parse(document.getElementById('order-data').textContent),original=root.innerHTML,byGid=new Map(data.decisions.map(x=>[String(x.gid),x])),position=new Map(data.gidOrder.map((x,i)=>[String(x),i]));let dragGroup=null,dragItem=null;const groups=()=>[...root.querySelectorAll(':scope>.group')];
function refresh(){{const q=document.getElementById('search').value.trim().toLowerCase();let count=0;[...document.querySelectorAll('.item')].forEach(x=>{{const show=!q||x.dataset.search.includes(q);x.classList.toggle('hidden',!show);if(show)count++}});groups().forEach(g=>{{const rows=[...g.querySelectorAll('.item')];g.classList.toggle('empty-group',rows.length===0);g.classList.toggle('hidden',rows.length>0&&!rows.some(x=>!x.classList.contains('hidden')));if(q&&!g.classList.contains('hidden')&&g.matches('details'))g.open=true}});const n=pinnedItems.querySelectorAll('.item').length;pinned.hidden=n===0;document.getElementById('pinned-count').textContent=n;document.getElementById('visible').textContent=count;document.getElementById('empty').hidden=count!==0}}
function pinItem(item){{pinnedItems.prepend(item);item.draggable=false;item.querySelector('.pin').textContent='归位';refresh();pinned.scrollIntoView({{behavior:'smooth',block:'start'}})}}function restore(item){{const g=groups().find(x=>x.dataset.seriesId===item.dataset.seriesId);if(!g)return;const parent=g.matches('details')?g.querySelector('.members'):g;parent.append(item);[...parent.querySelectorAll('.item')].sort((a,b)=>position.get(a.dataset.gid)-position.get(b.dataset.gid)).forEach(x=>parent.append(x));item.draggable=item.classList.contains('child');item.querySelector('.pin').textContent='单本置顶';refresh();g.scrollIntoView({{behavior:'smooth',block:'center'}})}}
function bind(){{groups().forEach(g=>{{g.addEventListener('dragstart',e=>{{const item=e.target.closest('.child');if(item&&g.open){{dragItem=item;item.classList.add('dragging');e.stopPropagation()}}else{{dragGroup=g;g.classList.add('dragging')}}}});g.addEventListener('dragend',()=>{{if(dragItem)dragItem.classList.remove('dragging');g.classList.remove('dragging');dragGroup=dragItem=null;document.querySelectorAll('.drop-before,.item-drop').forEach(x=>x.classList.remove('drop-before','item-drop'))}})}});root.querySelectorAll('.child').forEach(item=>{{item.addEventListener('dragover',e=>{{if(dragItem&&dragItem!==item&&dragItem.closest('.group')===item.closest('.group')){{e.preventDefault();item.classList.add('item-drop')}}}});item.addEventListener('drop',e=>{{if(dragItem&&dragItem!==item&&dragItem.closest('.group')===item.closest('.group')){{e.preventDefault();item.parentNode.insertBefore(dragItem,item)}}}})}});root.querySelectorAll('.pin').forEach(button=>button.addEventListener('click',e=>{{const item=e.target.closest('.item');pinnedItems.contains(item)?restore(item):pinItem(item)}}))}}
root.addEventListener('dragover',e=>{{if(!dragGroup)return;const target=e.target.closest('.group');if(target&&target!==dragGroup){{e.preventDefault();target.classList.add('drop-before')}}}});root.addEventListener('drop',e=>{{if(!dragGroup)return;const target=e.target.closest('.group');if(target&&target!==dragGroup){{e.preventDefault();root.insertBefore(dragGroup,target)}}}});document.getElementById('search').addEventListener('input',refresh);document.getElementById('expand').onclick=()=>groups().forEach(x=>{{if(x.matches('details'))x.open=true}});document.getElementById('collapse').onclick=()=>groups().forEach(x=>{{if(x.matches('details'))x.open=false}});document.getElementById('reset').onclick=()=>{{if(confirm('恢复页面打开时的顺序？')){{pinnedItems.innerHTML='';root.innerHTML=original;bind();refresh()}}}};
document.getElementById('export').onclick=()=>{{const gidOrder=[...pinnedItems.querySelectorAll('.item'),...root.querySelectorAll('.item')].map(x=>Number(x.dataset.gid));if(gidOrder.length!=={len(items)}||new Set(gidOrder).size!=={len(items)})return alert('GID 数量或唯一性校验失败');const seriesOrder=[],seen=new Set();gidOrder.forEach(gid=>{{const id=byGid.get(String(gid)).canonicalSeriesId;if(!seen.has(id)){{seen.add(id);seriesOrder.push(id)}}}});const payload={{formatVersion:2,snapshotFingerprint:data.snapshotFingerprint,reviewStatus:'human-adjusted-export',exportedAt:new Date().toISOString(),decisions:gidOrder.map((gid,i)=>({{...byGid.get(String(gid)),manualPosition:i+1}})),seriesOrder,gidOrder}};const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([JSON.stringify(payload,null,2)+'\\n'],{{type:'application/json'}}));a.download='ehviewer-series-order.json';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000)}};bind();refresh()}})();
</script></body></html>'''
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(page, encoding="utf-8")
    print(json.dumps({"output": str(args.output), "bytes": args.output.stat().st_size, "itemCount": len(items), "seriesCount": multi, "lowConfidenceCount": low}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
