import gzip
import json
import tempfile
import unittest
from pathlib import Path
from entity_research import report


class ResearchReportTest(unittest.TestCase):
    def test_reward_categories(self):
        messages = [
            ("+9,605 Hunting Experience", "Hunting XP"),
            ("Safari Essence x14", "Safari Essence"),
            ("Shyworm Shard", "Shard"),
            ("Polaris Shard x2", "Shard"),
            ("Shining Coin", "Item"),
            ("Icebreaker", "Item"),
        ]
        batch = dict(k="er", schema=1, c=1, v=1, world=1,
            events=[dict(sequence=i+1, time=i, action="floor-reward",
                data=dict(message=f"FLOOR DROP! You found {reward} on the ground!",
                    candidates=[], attribution="unmatched"))
                for i, (reward, _) in enumerate(messages)])
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "rewards.jsonl"
            path.write_text(json.dumps(batch), encoding="utf-8")
            result = report([path])
        self.assertEqual([r["category"] for r in result["floorRewards"]],
                         [category for _, category in messages])

    def test_candidates_scope_gaps_and_replies(self):
        events = [
            ("floor-components", dict(x=1, y=2, z=3, networkIds=[90, 91, 92])),
            ("floor-not-observed", dict(x=1, y=2, z=3, networkIds=[90, 91, 92])),
            ("floor-reward", dict(message="FLOOR DROP! You found +9,605 Hunting Experience on the ground!",
                candidates=["1,2,3 ageMs=100"], attribution="single-pending-candidate")),
            ("floor-reward", dict(message="FLOOR DROP! You found Shining Coin on the ground!",
                candidates=["1,2,3 ageMs=200", "4,5,6 ageMs=100"], attribution="ambiguous")),
            ("scrappy-reply", "The Scrappy ate the Wholesale Wheat and came out of its shell!"),
            ("gap", "cap"),
        ]
        batch = dict(k="er", schema=1, c=1, v=2, world=1,
            events=[dict(sequence=i+1, time=100+i, action=a, data=d) for i, (a, d) in enumerate(events)])
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.jsonl.gz"
            with gzip.open(path, "wt", encoding="utf-8") as sink:
                sink.write(json.dumps(batch) + "\n\n")
                batch["v"] = 3
                batch["events"] = [dict(sequence=1, time=200, action=events[2][0], data=events[2][1])]
                sink.write(json.dumps(batch) + "\n\n")
                sink.write("not json\n")
            result = report([path])
        self.assertEqual(result["floorRewards"][0]["relativeIds"], [0, 1, 2])
        self.assertEqual(result["floorRewards"][0]["category"], "Hunting XP")
        self.assertEqual(result["floorRewards"][1]["candidateNetworkIds"], [])
        self.assertEqual(result["floorRewards"][2]["candidateNetworkIds"], [])
        self.assertEqual(len(result["scrappyReplies"]), 1)
        self.assertEqual(len(result["gaps"]), 2)


if __name__ == "__main__":
    unittest.main()
