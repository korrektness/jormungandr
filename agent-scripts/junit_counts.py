import sys
import xml.etree.ElementTree as ET

ASSERTION_TYPES = ("org.opentest4j.AssertionFailedError",)
ASSERTION_SUFFIX = "ComparisonFailure"


def is_assertion_type(failure_type):
    return failure_type in ASSERTION_TYPES or failure_type.endswith(ASSERTION_SUFFIX)


def is_golden_mismatch(node):
    """Whether a <failure> or <error> node is golden-file mismatches only.

    A test that fails several golden assertions reaches the XML as one
    DefaultMultiCauseException whose message lists each cause as "type: message"
    on its own line after a header line.
    """
    failure_type = node.get("type", "")
    if is_assertion_type(failure_type):
        return True
    if not failure_type.endswith("MultiCauseException"):
        return False
    causes = [line.strip() for line in (node.get("message") or "").splitlines()[1:] if line.strip()]
    return bool(causes) and all(is_assertion_type(cause.split(":", 1)[0]) for cause in causes)


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
