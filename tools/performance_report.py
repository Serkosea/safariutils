"""Read-only aggregate of shared perf schema 1 records. Nested stage times are inclusive."""
import argparse
import collections
import gzip
import json


def report(paths):
    stages = collections.defaultdict(lambda: dict(calls=0, elapsedMs=0.0, maxMs=0.0))
    contexts = collections.defaultdict(lambda: dict(windows=0, frames=0, frameMs=0.0, maxFrameMs=0.0, worstWindowP95UpperMs=0.0))
    dropped = 0
    for path in paths:
        opener = gzip.open if str(path).endswith(".gz") else open
        with opener(path, "rt", encoding="utf-8") as stream:
            for line in stream:
                if not line.strip():
                    continue
                data = json.loads(line)
                if data.get("k") != "perf":
                    continue
                if data.get("schema") != 1:
                    raise ValueError("Unsupported performance schema")
                dropped += data["droppedStages"]
                context = contexts[data["context"]]
                context["windows"] += 1
                for stage in data["stages"]:
                    timing = stage["timing"]
                    entry = stages[stage["name"]]
                    entry["calls"] += timing["calls"]
                    entry["elapsedMs"] += timing["calls"] * timing["meanMs"]
                    entry["maxMs"] = max(entry["maxMs"], timing["maxMs"])
                    if stage["name"] == "client/frame/interval":
                        context["frames"] += timing["calls"]
                        context["frameMs"] += timing["calls"] * timing["meanMs"]
                        context["maxFrameMs"] = max(context["maxFrameMs"], timing["maxMs"])
                        context["worstWindowP95UpperMs"] = max(context["worstWindowP95UpperMs"], timing["p95UpperMs"])
    for context in contexts.values():
        context["fps"] = 1000 * context["frames"] / context["frameMs"] if context["frameMs"] else None
    return dict(contexts=dict(contexts), stages=dict(sorted(stages.items(), key=lambda pair: -pair[1]["elapsedMs"])),
                droppedStageSamples=dropped, caution="Inclusive wall times; do not sum nested scopes or interpret worker network waits as CPU cost.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("logs", nargs="+")
    args = parser.parse_args()
    print(json.dumps(report(args.logs), indent=2))
