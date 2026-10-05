"""Extract one reviewed changelog entry for publishing a release."""

import re
import sys
from pathlib import Path


def release_notes(changelog: str, version: str) -> str:
    heading = re.compile(r"^## \[?" + re.escape(version) + r"(?:\]|\s|$)")
    lines = changelog.splitlines()
    for start, line in enumerate(lines):
        if heading.match(line):
            end = next(
                (i for i in range(start + 1, len(lines)) if lines[i].startswith("## ")),
                len(lines),
            )
            # Reference definitions live at the file's end, not in release notes.
            body = [line for line in lines[start + 1 : end] if not re.match(r"^\[[^]]+\]:", line)]
            notes = "\n".join(body).strip()
            if not notes:
                raise ValueError(f"Changelog entry for {version} is empty")
            return notes + "\n"
    raise ValueError(f"No changelog entry for {version}")


if __name__ == "__main__":
    print(release_notes(Path("CHANGELOG.md").read_text(), sys.argv[1]), end="")
