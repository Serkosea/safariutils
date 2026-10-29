import gzip
import json
import tempfile
import unittest
from pathlib import Path
from performance_report import report


class PerformanceReportTest(unittest.TestCase):
    def test_weighted_frames_workers_and_bounds(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.jsonl.gz"
            with gzip.open(path, "wt", encoding="utf-8") as output:
                for calls, mean in [(100, 10), (50, 20)]:
                    output.write(json.dumps(dict(k="perf", schema=1, context="world/focused", droppedStages=2,
                        stages=[dict(name="client/frame/interval", timing=dict(calls=calls, meanMs=mean, maxMs=100, p95UpperMs=21)),
                                dict(name="worker/media", timing=dict(calls=1, meanMs=5000, maxMs=5000, p95UpperMs=5000))])) + "\n\n")
            result = report([path])
        self.assertEqual(result["contexts"]["world/focused"]["fps"], 75)
        self.assertEqual(result["contexts"]["world/focused"]["maxFrameMs"], 100)
        self.assertEqual(result["stages"]["worker/media"]["elapsedMs"], 10000)
        self.assertEqual(result["droppedStageSamples"], 4)


if __name__ == "__main__":
    unittest.main()
