import hashlib
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_kotlin_inputs import InputProblem, reconcile


class KotlinInputsTest(unittest.TestCase):
    def setUp(self):
        temp = TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.checkout = Path(temp.name).resolve()
        self.run = self.checkout / "run"
        self.main = self.run / "app/compileKotlin"
        self.empty = self.run / "packaging/compileKotlin"
        self.main.mkdir(parents=True)
        self.empty.mkdir(parents=True)
        (self.run / "expected.tsv").write_text("production-kotlin-v1\n:app:compileKotlin\n:packaging:compileKotlin\n")
        self.app = self.source("app/nonstandard/Only.kt", "fun value() = 1\n".encode())
        self.generated = self.source("app/build/generated/Only.kt", b"\xef\xbb\xbf// \xf0\x9f\x90\x8d\r\n")
        self.task = self.main / "task.tsv"
        self.compiler = self.main / "compiler.tsv"
        self.task.write_text(f"task-inputs-v1\tsources\n{self.app}{self.generated}")
        self.compiler.write_text(self.compiler_rows(self.generated, self.app))
        self.empty_task = self.empty / "task.tsv"
        self.empty_task.write_text("task-inputs-v1\tconfirmed-empty\n")

    def source(self, name, content):
        file = self.checkout / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_bytes(content)
        return f"{file}\t{hashlib.sha256(content).hexdigest()}\n"

    def compiler_rows(self, *task_rows):
        rows = ["compiler-inputs-v1\n"]
        for row in task_rows:
            path, digest = row.strip().split("\t")
            text = Path(path).read_bytes().decode("utf-8-sig").replace("\r\n", "\n").replace("\r", "\n")
            rows.append(f"source\t{path}\t{digest}\t{hashlib.sha256(text.encode()).hexdigest()}\n")
        return "".join(rows)

    def problem(self, code):
        with self.assertRaises(InputProblem) as caught:
            reconcile(self.run, self.checkout)
        self.assertEqual(caught.exception.details["problem"], code)
        return caught.exception.details

    def test_full_paths_reconcile_independently_of_row_order(self):
        result = reconcile(self.run, self.checkout)
        files = result[":app:compileKotlin"]["files"]
        self.assertEqual(
            {(row["root"], row["path"], row["sha256"]) for row in files},
            {("checkout", "app/nonstandard/Only.kt", self.app.split()[1]),
             ("checkout", "app/build/generated/Only.kt", self.generated.split()[1])},
        )
        self.assertEqual(result[":packaging:compileKotlin"], {"state": "confirmed-empty", "files": []})

    def test_omitted_generated_file_is_not_covered_by_same_basename(self):
        self.compiler.write_text(self.compiler_rows(self.app))
        missing = self.problem("input_mismatch")["missing"]
        self.assertEqual(missing, [str(self.checkout / "app/build/generated/Only.kt")])

    def test_unexpected_compiler_file_is_not_ignored(self):
        extra = self.source("app/Extra.kt", b"// extra\n")
        self.compiler.write_text(self.compiler_rows(self.app, self.generated, extra))
        self.assertEqual(self.problem("input_mismatch")["extra"], [str(self.checkout / "app/Extra.kt")])

    def test_spec_tag_cannot_exclude_an_extra_compiler_file(self):
        extra = self.source("app/Extra.kt", b"// extra\n")
        compiler_manifest = self.compiler_rows(self.app, self.generated)
        extra_row = self.compiler_rows(extra).splitlines()[1].replace("source\t", "spec\t", 1)
        self.compiler.write_text(f"{compiler_manifest}{extra_row}\n")
        issue = self.problem("unexpected_file_kind")
        self.assertEqual((issue["path"], issue["kind"]), (str(self.checkout / "app/Extra.kt"), "spec"))

    def test_missing_empty_task_is_not_confirmed_empty(self):
        self.empty_task.unlink()
        self.assertEqual(self.problem("missing_manifest")["manifest"], str(self.empty_task))

    def test_compiler_evidence_conflicts_with_an_empty_task_record(self):
        unexpected = self.empty / "compiler.tsv"
        unexpected.write_text(self.compiler_rows(self.generated))
        self.assertEqual(self.problem("compiler_ran_for_empty_task")["compilation"], ":packaging:compileKotlin")

    def test_missing_compiler_manifest_is_not_confirmed_empty(self):
        self.compiler.unlink()
        self.assertEqual(self.problem("missing_manifest")["manifest"], str(self.compiler))

    def test_malformed_manifest_reports_its_line_ending_defect(self):
        for ending, reason in (("\r\n", "carriage_return"), ("", "missing_final_newline")):
            with self.subTest(ending=ending):
                self.empty_task.write_bytes(f"task-inputs-v1\tconfirmed-empty{ending}".encode())
                issue = self.problem("malformed_manifest")
                self.assertEqual((issue["manifest"], issue["reason"]), (str(self.empty_task), reason))

    def test_empty_record_with_rows_reports_the_row_count(self):
        self.empty_task.write_text(f"task-inputs-v1\tconfirmed-empty\n{self.generated}")
        issue = self.problem("malformed_manifest")
        self.assertEqual((issue["compilation"], issue["row_count"]), (":packaging:compileKotlin", 1))

    def test_invalid_registry_identifies_the_rejected_task_id(self):
        invalid = ":app:compileTestKotlin"
        (self.run / "expected.tsv").write_text(f"production-kotlin-v1\n{invalid}\n")
        self.assertEqual(self.problem("invalid_registry")["invalid_ids"], [invalid])

    def test_matching_manifests_do_not_cover_a_removed_source(self):
        removed = self.checkout / "app/build/generated/Only.kt"
        removed.unlink()
        self.assertEqual(self.problem("unreadable_source")["path"], str(removed))

    def test_retargeted_source_reports_the_resolved_path_even_when_bytes_match(self):
        declared = self.checkout / "app/nonstandard/Only.kt"
        actual = declared.with_name("Redirected.kt")
        declared.rename(actual)
        declared.symlink_to(actual.name)
        issue = self.problem("source_path_changed")
        self.assertEqual((issue["path"], issue["resolved"]), (str(declared), str(actual)))

    def test_compiler_and_task_must_agree_on_bytes(self):
        changed = self.source("app/build/generated/Only.kt", b"// different bytes\n")
        self.compiler.write_text(self.compiler_rows(self.app, changed))
        path, task_digest = self.generated.strip().split("\t")
        compiler_digest = changed.strip().split("\t")[1]
        self.assertEqual(self.problem("input_mismatch")["changed"],
                         {path: dict(task_sha256=task_digest, compiler_sha256=compiler_digest)})

    def test_compiler_normalized_text_must_match_the_raw_source(self):
        good = self.compiler_rows(self.app, self.generated)
        prefix, _ = good.rstrip().rsplit("\t", 1)
        self.compiler.write_text(f"{prefix}\t{'0' * 64}\n")
        self.assertEqual(self.problem("compiler_text_mismatch")["path"], str(self.checkout / "app/build/generated/Only.kt"))


if __name__ == "__main__":
    unittest.main()
