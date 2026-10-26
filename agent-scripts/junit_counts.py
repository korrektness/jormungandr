import sys
import xml.etree.ElementTree as ET

ASSERTION_TYPES = ("org.opentest4j.AssertionFailedError",)
ASSERTION_SUFFIX = "ComparisonFailure"
MULTIPLE_FAILURE_SUFFIXES = ("MultiCauseException", "MultipleFailuresError")


def is_assertion_type(failure_type):
    return failure_type in ASSERTION_TYPES or failure_type.endswith(ASSERTION_SUFFIX)


def is_golden_assertion(failure_type, message):
    if not is_assertion_type(failure_type):
        return False
    # Gradle and the runner can include Throwable.toString() in the message.
    message = message.removeprefix(failure_type + ": ")
    # Kotlin's file assertions use these messages for a mismatch or a newly
    # generated file. Unknown assertion messages fail closed as ordinary failures.
    prefixes = ("Actual data differs from file content: ",
                "Expected data file did not exist. Generating: ")
    return any(message.startswith(prefix) and message[len(prefix):].strip() for prefix in prefixes)


def is_golden_mismatch(node):
    """Whether a <failure> or <error> node is golden-file mismatches only.

    A test that fails several golden assertions reaches the XML as one
    exception whose message lists each cause as "type: message" on its own line
    after a header line: Gradle's DefaultMultiCauseException, or JUnit's
    MultipleFailuresError from the persistent test runner.
    """
    failure_type = node.get("type", "")
    if is_golden_assertion(failure_type, node.get("message") or ""):
        return True
    if not failure_type.endswith(MULTIPLE_FAILURE_SUFFIXES):
        return False
    causes = [line.strip() for line in (node.get("message") or "").splitlines()[1:] if line.strip()]
    return bool(causes) and all(
        is_golden_assertion(failure_type, message.lstrip())
        for failure_type, _, message in (cause.partition(":") for cause in causes)
    )


def main(paths):
    total = assertion_failed = other_failed = skipped = unreadable = 0

    # Counted from the <testcase> elements rather than the <testsuite> attributes:
    # a suite that died early still carries a count that its cases do not back.
    for path in paths:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            unreadable += 1
            continue
        for testcase in root.iter("testcase"):
            total += 1
            node = testcase.find("failure")
            if node is None:
                node = testcase.find("error")
            if node is None:
                if testcase.find("skipped") is not None:
                    skipped += 1
                continue
            if is_golden_mismatch(node):
                assertion_failed += 1
            else:
                other_failed += 1

    print(f"{total} {assertion_failed} {other_failed} {skipped} {unreadable}")


if __name__ == "__main__":
    main(sys.argv[1:])
