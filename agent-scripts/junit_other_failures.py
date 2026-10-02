import sys
import xml.etree.ElementTree as ET

# Importing a sibling script would leave a __pycache__ in the checkout.
sys.dont_write_bytecode = True
from junit_counts import is_golden_mismatch

# Print every failing <testcase> that is not a golden-file mismatch, as
# "classname.name: message", and every result file that cannot be parsed, since
# it may hold such a failure. Exits 1 when there is neither.
found = False
for path in sys.argv[1:]:
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        print(f"{path}: unreadable test result")
        found = True
        continue
    for testcase in root.iter("testcase"):
        node = testcase.find("failure")
        if node is None:
            node = testcase.find("error")
        if node is None or is_golden_mismatch(node):
            continue
        classname = testcase.get("classname", root.get("name", "?"))
        name = testcase.get("name", "?")
        print(f"{classname}.{name}: {node.get('message') or '(no message)'}")
        found = True

sys.exit(0 if found else 1)
