# SPDX-License-Identifier: GPL-3.0-or-later
"""Write an honest per-run summary, including setup and missing-test failures."""

import os
import platform
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

report = Path("target/surefire-reports/TEST-org.sarmanagement.icsforms.persistence.PlatformFilesystemTest.xml")
lines = [
    f"Case: {os.environ.get('PLATFORM_FILESYSTEM_CASE', 'unknown')}",
    f"Runner: {platform.platform()}",
    f"Image: {os.environ.get('ImageOS', 'unknown')} {os.environ.get('ImageVersion', 'unknown')}",
    f"Run: {os.environ.get('RUN_URL', 'unknown')}",
    f"Mount: {os.environ.get('MOUNT_RESULT', 'unknown')}",
    f"Test step: {os.environ.get('TEST_RESULT', 'unknown')}",
]
if report.exists():
    suite = ET.parse(report).getroot()
    lines.append("JUnit: " + ", ".join(
        f"{key}={suite.get(key, 'unknown')}" for key in ("tests", "failures", "errors", "skipped")
    ))
    for element in suite.iter("system-out"):
        if element.text:
            lines.append(element.text.strip())
else:
    lines.append("JUnit: no report produced")
summary = "\n".join(lines) + "\n"
Path("platform-results.txt").write_text(summary, encoding="utf-8")
with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as output:
    output.write("### Filesystem verification\n\n```\n" + summary + "```\n")
print(summary)
if (not report.exists() or os.environ.get("TEST_RESULT") != "success"
        or any(suite.get(key) != value for key, value in
               (("tests", "1"), ("failures", "0"), ("errors", "0"), ("skipped", "0")))):
    print("The required filesystem test did not pass exactly once.", file=sys.stderr)
    sys.exit(1)
