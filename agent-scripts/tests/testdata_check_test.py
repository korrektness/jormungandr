"""Structural checks exercise a disposable checkout without a compiler build."""

import pathlib
import shutil
import subprocess
import tempfile
import unittest


class TestDataCheckTest(unittest.TestCase):
    def test_program_goldens_need_a_source_and_runnable_discovery(self):
        scripts = pathlib.Path(__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            (root / "agent-scripts").mkdir()
            for name in ("check-testdata.sh", "lib.sh"):
                shutil.copyfile(scripts / name, root / "agent-scripts" / name)
            for directory in (
                "formver.compiler-plugin/testData/diagnostics",
                "formver.compiler-plugin/testData/programs",
                "formver.compiler-plugin/locality/testData/diagnostics",
            ):
                (root / directory).mkdir(parents=True)

            def check():
                return subprocess.run(
                    ["bash", str(root / "agent-scripts/check-testdata.sh")],
                    capture_output=True, text=True, check=False,
                ).returncode

            program = root / "formver.compiler-plugin/testData/programs/algorithms/search"
            program.mkdir(parents=True)
            golden = program / "search.viper.txt"
            golden.write_text("method search() {}\n")
            self.assertEqual(check(), 1)
            source = program / "search.kt"
            source.write_text("fun search() {}\n")
            self.assertEqual(check(), 0)

            support = root / "formver.compiler-plugin/testData/support/libraries"
            support.mkdir(parents=True)
            source.rename(support / source.name)
            golden.rename(support / golden.name)
            self.assertEqual(check(), 1)


if __name__ == "__main__":
    unittest.main()
