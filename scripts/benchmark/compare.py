#!/usr/bin/env python3
"""A/B report of a Macrobenchmark run on one device: baseline vs candidate build.

Usage: compare.py <out dir> --order base+candidate+candidate+base
                  [--markdown report.md] [--header "markdown line"]

Finds *benchmarkData.json anywhere under <out>. Both builds are
measured in one instrumentation run: test names end in "[<index>]", and --order says
which build and round each index was. Iterations of all rounds are pooled per build.

For each metric the report gives both medians (or frame percentiles), the change, and a
95% bootstrap confidence interval of that change, and judges it against fixed thresholds
(see RULES). The two rounds of the same build are also compared with each other: when a
build disagrees with itself (e.g. the phone heated up), its failures are downgraded to
warnings.

Writes the Markdown report to --markdown (else stdout) and prints GitHub annotations.
Exit code: 0 when nothing regressed (warnings allowed), 1 on a regression or no results.
"""
import json
import pathlib
import random
import re
import statistics
import sys

VARIANTS = ("base", "candidate")
MARKER = "<!-- hootbook-perf -->"
BOOTSTRAP = 2000

# Test -> label, in report order. Unknown tests are listed after these under their name.
SCENARIOS = {
    "startupWithProfile": "冷启动到首页（按 profile 编译）",
    "transactionsDataReady": "点交易 tab → 列表",
    "transactionsDataReadyWithProfile": "点交易 tab → 列表（按 profile 编译）",
    "transactionsScroll": "交易列表 · 正常速度滑动",
    "transactionsFling": "交易列表 · 最快速度甩动",
    "homeScroll": "首页 · 最快速度甩动",
    "analyticsOpenAndScroll": "分析页 · 打开并甩动",
    "tabSwitching": "底部 tab 来回切换",
    "settingsOpenAndScroll": "设置页 · 打开并甩动",
}

# Metric -> (label, kind). Kinds:
#   latency  one value per iteration, ms; judged on the relative change of the median
#   frames   per-frame samples, ms; judged on the absolute change of P90 and P99
#   count    one value per iteration; any increase of the median is a regression
#   info     shown, never judged
METRICS = {
    "timeToInitialDisplayMs": ("首帧显示 (TTID)", "latency"),
    "timeToFullDisplayMs": ("完全绘制 (TTFD)", "latency"),
    "tapToListDrawnMs": ("点击 → 列表画出（trace）", "latency"),
    "TransactionsList.publishCount": ("列表发布次数", "count"),
    "frameOverrunMs": ("帧超时", "frames"),
    "frameDurationCpuMs": ("帧 CPU 耗时", "info"),
}
FRAME_PERCENTILES = {"frames": (50, 90, 99), "info": (90,)}
JUDGED_PERCENTILES = (90, 99)

# Thresholds. A change counts only when the whole 95% interval is past the threshold.
RULES = {
    # Relative change of the median, and a minimum absolute change in ms.
    "latency": {"fail": (0.10, 10.0), "warn": (0.03, 5.0), "better": (0.03, 5.0)},
    # Absolute change of a frame percentile, in ms (8 ms is one frame at 120 Hz).
    "frames": {"fail": 8.0, "warn": 2.0, "better": 2.0},
    # Round-to-round disagreement of one build that makes a metric untrustworthy.
    "unstable": {"latency": 0.15, "frames": 8.0},
}

FAIL, WARN, BETTER, SAME, INFO, NODATA = "fail", "warn", "better", "same", "info", "nodata"
VERDICT_TEXT = {
    FAIL: "❌ 回退",
    WARN: "⚠️ 变慢",
    BETTER: "✅ 更快",
    SAME: "➖ 无显著差异",
    INFO: "—",
    NODATA: "base 无数据",
}


def pct(values, p):
    s = sorted(values)
    k = (len(s) - 1) * p / 100
    lo, hi = int(k), min(int(k) + 1, len(s) - 1)
    return s[lo] + (s[hi] - s[lo]) * (k - lo)


INDEX = re.compile(r"^(.*)\[(\d+)\]$")


def split_index(name):
    """"transactionsScroll[2]" -> ("transactionsScroll", 2); other names -> (name, None)."""
    m = INDEX.match(name)
    return (m.group(1), int(m.group(2))) if m else (name, None)


def owner(index, order):
    """(variant, round) of the test with this parameter index, or None."""
    if index is None or index >= len(order):
        return None
    variant = order[index]
    return variant, order[: index + 1].count(variant)


def collect(out, order):
    """{(test, metric): {variant: {round: [iteration values or per-frame lists]}}}."""
    data = {}

    def add(index, test, metric, iterations):
        who = owner(index, order)
        if who and who[0] in VARIANTS:
            rounds = data.setdefault((test, metric), {v: {} for v in VARIANTS})[who[0]]
            rounds[who[1]] = iterations

    for f in out.rglob("*benchmarkData.json"):
        for b in json.loads(f.read_text()).get("benchmarks", []):
            test, index = split_index(b["name"])
            for metric, m in b.get("metrics", {}).items():
                if metric != "frameCount" and m.get("runs"):
                    add(index, test, metric, [float(v) for v in m["runs"]])
            for metric, m in b.get("sampledMetrics", {}).items():
                runs = [[float(v) for v in r] for r in m.get("runs", []) if r]
                if runs:
                    add(index, test, metric, runs)
    return data


def pooled(rounds):
    return [x for r in sorted(rounds) for x in rounds[r]]


def bootstrap(rng, base, cand, stat):
    """95% interval of stat(candidate) - stat(base), resampling iterations."""
    diffs = []
    for _ in range(BOOTSTRAP):
        b = rng.choices(base, k=len(base))
        c = rng.choices(cand, k=len(cand))
        diffs.append(stat(c) - stat(b))
    diffs.sort()
    return diffs[int(0.025 * BOOTSTRAP)], diffs[int(0.975 * BOOTSTRAP) - 1]


def frames_stat(p):
    """Percentile p of all frames of the given iterations, frames resampled within each."""
    rng = random.Random(p)

    def stat(iterations):
        frames = []
        for it in iterations:
            frames.extend(rng.choices(it, k=len(it)))
        return pct(frames, p)

    return stat


def round_gap(kind, rounds, stat):
    """How far apart the rounds of one build are: relative for latency, ms for frames."""
    values = [stat(rounds[r]) for r in sorted(rounds) if rounds[r]]
    if len(values) < 2:
        return None
    if kind == "latency":
        return abs(values[-1] / values[0] - 1) if values[0] else None
    return abs(values[-1] - values[0])


def judge_latency(b, c, lo, hi):
    diff, rule = c - b, RULES["latency"]
    if lo / b > rule["fail"][0] and diff > rule["fail"][1]:
        return FAIL
    if lo / b > rule["warn"][0] and diff > rule["warn"][1]:
        return WARN
    if hi / b < -rule["better"][0] and -diff > rule["better"][1]:
        return BETTER
    return SAME


def judge_frames(lo, hi):
    rule = RULES["frames"]
    if lo > rule["fail"]:
        return FAIL
    if lo > rule["warn"]:
        return WARN
    if hi < -rule["better"]:
        return BETTER
    return SAME


def ms(v):
    return f"{v:.1f} ms".replace("-", "−")


def signed_ms(v):
    return f"{v:+.1f} ms".replace("-", "−")


def signed_pct(v):
    return f"{v * 100:+.0f}%".replace("-", "−")


def rows_for(rng, test, metric, per_variant):
    label, kind = METRICS.get(metric, (metric, "info"))
    base_rounds, cand_rounds = per_variant["base"], per_variant["candidate"]
    rows = []

    def row(name, b, c, change, interval, verdict, gaps=()):
        unstable = any(g is not None and g > RULES["unstable"].get(kind, float("inf")) for g in gaps)
        if unstable and verdict == FAIL:
            verdict = WARN
        rows.append({
            "test": test, "metric": name, "base": b, "candidate": c, "change": change,
            "interval": interval, "verdict": verdict, "unstable": unstable,
        })

    if not cand_rounds:
        return rows
    if kind in ("frames", "info") and isinstance(pooled(cand_rounds)[0], list):
        cand = pooled(cand_rounds)
        base = pooled(base_rounds)
        for p in FRAME_PERCENTILES.get(kind, (90,)):
            name = f"{label} P{p}"
            c = pct([x for it in cand for x in it], p)
            if not base:
                row(name, "—", ms(c), "—", "—", NODATA)
                continue
            b = pct([x for it in base for x in it], p)
            if kind == "info" or p not in JUDGED_PERCENTILES:
                row(name, ms(b), ms(c), signed_ms(c - b), "—", INFO)
                continue
            stat = frames_stat(p)
            lo, hi = bootstrap(rng, base, cand, stat)
            gaps = [round_gap("frames", r, lambda its: pct([x for it in its for x in it], p))
                    for r in (base_rounds, cand_rounds)]
            row(name, ms(b), ms(c), signed_ms(c - b),
                f"{signed_ms(lo)} … {signed_ms(hi)}", judge_frames(lo, hi), gaps)
        return rows

    cand, base = pooled(cand_rounds), pooled(base_rounds)
    c = statistics.median(cand)
    b = statistics.median(base) if base else None
    if kind == "count":
        # Trace sections only exist in builds that emit them: 0 means "not emitted".
        if not b:
            row(label, "—", f"{c:g}", "—", "—", NODATA)
        else:
            row(label, f"{b:g}", f"{c:g}", f"{c - b:+g}", "确定值", FAIL if c > b else SAME)
        return rows
    if b is None or b == 0:
        row(label, "—", ms(c), "—", "—", NODATA)
        return rows
    if kind != "latency":
        row(label, ms(b), ms(c), f"{signed_ms(c - b)} ({signed_pct(c / b - 1)})", "—", INFO)
        return rows
    lo, hi = bootstrap(rng, base, cand, statistics.median)
    gaps = [round_gap("latency", r, statistics.median) for r in (base_rounds, cand_rounds)]
    row(label, ms(b), ms(c), f"{signed_ms(c - b)} ({signed_pct(c / b - 1)})",
        f"{signed_pct(lo / b)} … {signed_pct(hi / b)}", judge_latency(b, c, lo, hi), gaps)
    return rows


def sort_key(item):
    (test, metric), _ = item
    order = list(SCENARIOS)
    metrics = list(METRICS)
    return (order.index(test) if test in order else len(order), test,
            metrics.index(metric) if metric in metrics else len(metrics), metric)


def report(data, header):
    rng = random.Random(0)
    rows = []
    for (test, metric), per_variant in sorted(data.items(), key=sort_key):
        rows.extend(rows_for(rng, test, metric, per_variant))

    fails = [r for r in rows if r["verdict"] == FAIL]
    warns = [r for r in rows if r["verdict"] == WARN]
    # An unstable metric needs a look unless it is already a warning or clearly better.
    unstable = [r for r in rows if r["unstable"] and r["verdict"] not in (WARN, BETTER)]
    better = [r for r in rows if r["verdict"] == BETTER]
    if not rows:
        title = "❌ 没有拿到任何结果"
    elif fails:
        title = f"❌ {len(fails)} 项回退"
    elif warns or unstable:
        title = f"⚠️ {len(warns) + len(unstable)} 项需要注意"
    else:
        title = "✅ 无回退"
    if better:
        title += f"，{len(better)} 项更快"

    lines = [MARKER, f"## 性能 A/B：{title}", ""]
    if header:
        lines += [header, ""]
    if rows:
        lines += ["| 场景 | 指标 | base | candidate | 变化 | 95% 区间 | 判定 |",
                  "|---|---|---:|---:|---:|:---:|:---:|"]
        last = None
        for r in rows:
            scenario = SCENARIOS.get(r["test"], r["test"]) if r["test"] != last else ""
            last = r["test"]
            verdict = VERDICT_TEXT[r["verdict"]] + ("（本次不稳定）" if r["unstable"] else "")
            lines.append(f"| {scenario} | {r['metric']} | {r['base']} | {r['candidate']} | "
                         f"{r['change']} | {r['interval']} | {verdict} |")
    lat, fr, un = RULES["latency"], RULES["frames"], RULES["unstable"]
    lines += [
        "",
        "<details><summary>怎么读</summary>",
        "",
        "- 两个版本在同一台手机上按 base、candidate、candidate、base 交替各跑两轮，每个指标把两轮合在一起比较。",
        "- 95% 区间：对各次迭代做 bootstrap 重抽样得到的“candidate − base”的范围；整个区间都越过阈值才算变化。",
        f"- 延迟（中位数）：区间下限 > +{lat['fail'][0]:.0%} 且差值 > {lat['fail'][1]:g} ms 为 ❌ 回退，"
        f"> +{lat['warn'][0]:.0%} 且 > {lat['warn'][1]:g} ms 为 ⚠️ 变慢；区间上限 < −{lat['better'][0]:.0%} 为 ✅ 更快。",
        f"- 帧超时 P90 / P99：区间下限 > +{fr['fail']:g} ms（120 Hz 下一帧）为 ❌，> +{fr['warn']:g} ms 为 ⚠️；"
        f"上限 < −{fr['better']:g} ms 为 ✅。P50 和帧 CPU 耗时只作参考。",
        "- 列表发布次数是确定值，比 base 多就是 ❌。",
        f"- 同一版本两轮之间差 > {un['latency']:.0%}（延迟）或 > {un['frames']:g} ms（帧）时标为“本次不稳定”，"
        "❌ 降级为 ⚠️，建议重跑。",
        "- “base 无数据”：base 版本还没有这个 trace 标记，不参与判定。",
        "",
        "</details>",
    ]
    return "\n".join(lines) + "\n", rows, fails, warns, unstable


def annotate(rows, fails, warns, unstable):
    for level, items in (("error", fails), ("warning", warns + unstable)):
        for r in items:
            scenario = SCENARIOS.get(r["test"], r["test"])
            note = "（本次不稳定）" if r["unstable"] else ""
            title = VERDICT_TEXT[r["verdict"]] if r["verdict"] in (FAIL, WARN) else "本次不稳定"
            print(f"::{level} title=性能 {title}::"
                  f"{scenario} · {r['metric']}: {r['base']} → {r['candidate']} "
                  f"({r['change']}, 95% {r['interval']}){note}")


def main(argv):
    args = list(argv)

    def option(name):
        if name not in args:
            return None
        i = args.index(name)
        value = args[i + 1]
        del args[i : i + 2]
        return value

    order, markdown, header = option("--order"), option("--markdown"), option("--header")
    if not order:
        sys.exit(__doc__)
    data = collect(pathlib.Path(args[0] if args else "out"), order.split("+"))
    text, rows, fails, warns, unstable = report(data, header)
    if markdown:
        pathlib.Path(markdown).write_text(text)
    else:
        print(text)
    annotate(rows, fails, warns, unstable)
    return 1 if fails or not rows else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
