import sys
import xml.etree.ElementTree as ET

# Importing a sibling script would leave a __pycache__ in the checkout.
sys.dont_write_bytecode = True
from junit_counts import is_golden_mismatch

# Prints "golden" or "other" on the first line: whether every cause of the
# failure is a golden-file mismatch, which one or several assertions can be.
# Then "classname.name: message", then the stack trace.

for path in sys.argv[1:]:
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    # Gradle emits a bare <testsuite>, but a <testsuites> wrapper would put the
    # cases a level deeper, and finding nothing reads as a clean run.
    for testcase in root.iter("testcase"):
        node = testcase.find("failure")
        if node is None:
            node = testcase.find("error")
        if node is None:
            continue
        classname = testcase.get("classname", root.get("name", "?"))
        name = testcase.get("name", "?")
        message = node.get("message") or "(no message)"
        print("golden" if is_golden_mismatch(node) else "other")
        print(f"{classname}.{name}: {message}")
        # The trace opens by restating the message; printing it twice buries
        # the frames that say where it came from.
        trace = (node.text or "").strip().splitlines()
        while trace and trace[0].strip() == message.strip():
            trace.pop(0)
        for line in trace[:8]:
            print(f"    {line}")
        sys.exit(0)

sys.exit(1)
