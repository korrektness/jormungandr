#!/usr/bin/env python3
"""Exercise the real test driver with isolated Gradle and runner stand-ins.

Neither backend compiles code or starts a verifier. Result timestamps use
explicit ticks relative to the driver's marker, independent of filesystem time.
"""

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


SCRIPTS = Path(__file__).resolve().parents[1]
BACKEND = '''#!/usr/bin/env python3
import json
import os
from pathlib import Path
import sys

root = Path.cwd()
args = sys.argv[1:]
if any(arg.endswith("RunnerSpec") for arg in args):
    sys.exit(0)
module = "locality" if any("locality" in arg for arg in args) else "compiler"
scenario = json.loads((root / "scenario.json").read_text())[module]
# The driver's mktemp marker is in our private TMPDIR. Explicit ticks keep
# both fresh and stale result cases deterministic without sleeping.
for marker in (root / "tmp").glob("tmp.*"):
    if marker.is_file():
        os.utime(marker, ns=(1000000000, 1000000000))
results = root / "formver.compiler-plugin"
if module == "locality":
    results /= "locality"
results /= "build/test-results/test"
results.mkdir(parents=True, exist_ok=True)
for index, report in enumerate(scenario.get("reports", [])):
    path = results / f"TEST-{index}.xml"
    path.write_text(report["xml"])
    tick = 500000000 if report.get("stale") else 2000000000
    os.utime(path, ns=(tick, tick))
print(scenario.get("output", ""))
sys.exit(scenario.get("status", 0))
'''

PASS = '<testsuite><testcase classname="Example" name="passes"/></testsuite>'
GOLDEN = '''<testsuite><testcase classname="Example" name="golden">
<failure type="org.opentest4j.AssertionFailedError" message="org.opentest4j.AssertionFailedError: Actual data differs from file content: example.kt"/>
</testcase></testsuite>'''
FAIL = '''<testsuite><testcase classname="Example" name="broken">
<error type="java.lang.IllegalStateException" message="broken fixture"/>
</testcase></testsuite>'''


def result(*xml, status=0, output="", stale=False):
    return {"reports": [{"xml": text, "stale": stale} for text in xml],
            "status": status, "output": output}


class TestDriverTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="test-driver-")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        scripts = self.root / "agent-scripts"
        scripts.mkdir()
        for source in SCRIPTS.glob("*"):
            if source.is_file():
                shutil.copy2(source, scripts / source.name)
        # Git hooks export checkout-specific state. Neither that state nor the
        # host's Git configuration belongs to this fixture repository.
        self.environment = {key: value for key, value in os.environ.items()
                            if not key.startswith("GIT_")}
        self.environment.update(GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull,
                                GIT_AUTHOR_DATE="2000-01-01T00:00:00Z",
                                GIT_COMMITTER_DATE="2000-01-01T00:00:00Z")
        # Update-mode reporting inspects Git, but never needs the real checkout.
        subprocess.run(["git", "init", "-q", str(self.root)], check=True, env=self.environment)
        subprocess.run(["git", "-C", str(self.root), "-c", "user.name=Test",
                        "-c", "user.email=test@example.invalid", "commit", "-q",
                        "--allow-empty", "-m", "fixture"], check=True, env=self.environment)
        self.executable(self.root / "gradlew", BACKEND)
        for module in ("formver.compiler-plugin", "formver.compiler-plugin/locality"):
            for task in ("test", "untilConversion"):
                self.executable(self.root / module / "build/test-runner" / task / "client", BACKEND)
        # The data checker is outside this driver's contract.
        self.executable(scripts / "check-testdata.sh", "#!/bin/sh\nexit 0\n")
        (self.root / "tmp").mkdir()

    def executable(self, path, text):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        path.chmod(0o755)

    def run_driver(self, compiler, locality=None, *, backend="off", update=False, pattern=None):
        # Each invocation gets its own reports, even within a subtest matrix.
        for results in self.root.glob("**/test-results"):
            shutil.rmtree(results)
        (self.root / "scenario.json").write_text(json.dumps({
            "compiler": compiler,
            "locality": result(PASS) if locality is None else locality,
        }))
        environment = dict(self.environment, SNAKT_TEST_RUNNER=backend,
                           SNAKT_VERIFICATION_CACHE="off",
                           SNAKT_TEST_DUMP_DIR=str(self.root / "dumps"),
                           TMPDIR=str(self.root / "tmp"))
        args = [str(self.root / "agent-scripts/test.sh")]
        if update:
            args.append("--update-goldens")
        if pattern:
            args.append(pattern)
        return subprocess.run(args, cwd=self.root, env=environment,
                              text=True, capture_output=True)

    def assert_status(self, expected, completed):
        self.assertEqual(expected, completed.returncode, completed.stdout + completed.stderr)

    def test_success_requires_fresh_complete_nonempty_results_from_each_module(self):
        invalid = {
            "missing": result(),
            "stale": result(PASS, stale=True),
            "empty": result("<testsuite/>"),
            "malformed": result("<testsuite>"),
            "partial": result(PASS, "<testsuite>"),
        }
        for backend in ("on", "off"):
            for update in (False, True):
                with self.subTest(backend=backend, update=update, case="control"):
                    self.assert_status(0, self.run_driver(result(PASS), backend=backend, update=update))
                for name, broken in invalid.items():
                    for module in ("compiler", "locality"):
                        with self.subTest(backend=backend, update=update, case=name, module=module):
                            self.assert_status(1, self.run_driver(
                                broken if module == "compiler" else result(PASS),
                                broken if module == "locality" else result(PASS),
                                backend=backend, update=update))

    def test_xml_failure_cannot_be_overridden_by_successful_backend_exit(self):
        for backend in ("on", "off"):
            for xml in (GOLDEN, FAIL):
                with self.subTest(backend=backend, xml=xml):
                    self.assert_status(1, self.run_driver(result(xml), backend=backend))

    def test_update_accepts_rewrites_but_not_unexplained_backend_failures(self):
        for backend in ("on", "off"):
            with self.subTest(backend=backend, case="rewrite"):
                self.assert_status(0, self.run_driver(result(GOLDEN, status=1),
                                                     backend=backend, update=True))
            assertions = (SCRIPTS / "tests/fixtures/generic-assertions.xml").read_text()
            for reports, status in (([PASS], 1), ([GOLDEN], 2), ([FAIL], 1), ([GOLDEN, FAIL], 1),
                                    ([assertions], 1)):
                with self.subTest(backend=backend, reports=reports, status=status):
                    self.assert_status(1, self.run_driver(result(*reports, status=status),
                                                         backend=backend, update=True))

    def test_filter_can_match_one_module_but_must_match_somewhere(self):
        missing = result(status=1, output="No tests found for given includes: [*Example*]")
        for backend in ("on", "off"):
            for update in (False, True):
                with self.subTest(backend=backend, update=update):
                    self.assert_status(0, self.run_driver(result(PASS), missing, backend=backend,
                                                         update=update, pattern="Example"))
                    self.assert_status(1, self.run_driver(missing, missing, backend=backend,
                                                         update=update, pattern="Example"))
                    empty = result("<testsuite/>", status=1, output=missing["output"])
                    self.assert_status(0, self.run_driver(result(PASS), empty, backend=backend,
                                                         update=update, pattern="Example"))
                    for invalid in (dict(missing, status=2),
                                    result(FAIL, status=1, output=missing["output"]),
                                    result("<testsuite>", status=1, output=missing["output"])):
                        self.assert_status(1, self.run_driver(result(PASS), invalid, backend=backend,
                                                             update=update, pattern="Example"))


if __name__ == "__main__":
    unittest.main()
