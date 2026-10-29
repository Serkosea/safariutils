"""Read-only report for entity-research schema 1. Supply ordered gzip JSONL segments of one launch.
Does not infer prediction rules. Single pending clicks remain candidates, not proven attribution.
"""
import argparse
import collections
import gzip
import json
import re


def report(paths):
    counts = collections.Counter()
    groups = {}
    rewards = []
    interactions = []
    replies = []
    gaps = []
    sequences = {}
    for path in paths:
        opener = gzip.open if str(path).endswith(".gz") else open
        with opener(path, "rt", encoding="utf-8") as source:
            for number, line in enumerate(source, 1):
                if not line.strip():
                    continue
                try:
                    record = json.loads(line)
                except ValueError:
                    gaps.append(f"{path}:{number}: invalid JSON")
                    continue
                if record.get("k") != "er":
                    if "dropped=" in record.get("m", ""):
                        gaps.append(record["m"])
                    continue
                if record.get("schema") != 1:
                    gaps.append(f"Unsupported research schema: {record.get('schema')}")
                    continue
                scope = (record.get("c"), record.get("v"), record.get("world"))
                for event in record["events"]:
                    previous = sequences.get(scope)
                    if previous is not None and event["sequence"] != previous + 1:
                        gaps.append(f"Sequence gap in {scope}: {previous} -> {event['sequence']}")
                    sequences[scope] = event["sequence"]
                    action, data = event["action"], event["data"]
                    counts[action] += 1
                    if action == "gap":
                        gaps.append(str(data))
                    elif action == "floor-components":
                        groups[(scope, (data["x"], data["y"], data["z"]))] = data["networkIds"]
                    elif action == "floor-not-observed":
                        # Keep the last observed components for a later server reward.
                        pass
                    elif action == "floor-reward":
                        ids = []
                        if data["attribution"] == "single-pending-candidate":
                            xyz = tuple(map(int, data["candidates"][0].split(" ageMs=")[0].split(",")))
                            ids = groups.get((scope, xyz), [])
                        message = data["message"]
                        category = ("Hunting XP" if re.search(r"(?:Safari (?:XP|Experience)|Hunting Experience)", message, re.I)
                                    else "Safari Essence" if "essence" in message.lower()
                                    else "Shard" if re.search(r"You found .+ Shards?(?: x[\d,]+)? on the ground!", message, re.I)
                                    else "Item" if "You found " in message and "on the ground!" in message
                                    else "Unclassified")
                        rewards.append(dict(visit=scope[1], time=event["time"], category=category,
                                            attribution=data["attribution"], candidateNetworkIds=ids,
                                            relativeIds=[value-min(ids) for value in ids] if ids else [],
                                            message=message))
                    elif action == "interaction":
                        if data["target"]["type"].endswith(":armadillo") or any(food in data["name"] for food in
                            ("Wholesale Wheat", "Flavor-packed Fish", "Lush Lily Pad")):
                            interactions.append(dict(visit=scope[1], time=event["time"], **data))
                    elif action == "scrappy-reply":
                        replies.append(dict(visit=scope[1], time=event["time"], message=data))
    return dict(eventCounts=dict(counts), gaps=gaps, floorRewards=rewards,
                scrappyInteractions=interactions, scrappyReplies=replies,
                caution="Observed IDs/order only. Baselines are partial. Replies have no target ID. Validate on independent runs.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("logs", nargs="+")
    args = parser.parse_args()
    print(json.dumps(report(args.logs), indent=2, ensure_ascii=False))
