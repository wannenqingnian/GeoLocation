import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import textwrap
import unittest


WORKFLOW = Path(__file__).resolve().parents[1] / "workflows/release.yml"
BASH = shutil.which("bash")
if os.name == "nt":
    BASH = str(Path(shutil.which("git")).parents[1] / "bin/bash.exe")


def shell_step(name):
    match = re.search(
        rf"^      - name: {re.escape(name)}\n.*?^        run: \|\n((?:^          [^\n]*\n|^\n)*)",
        WORKFLOW.read_text(encoding="utf-8"), re.MULTILINE | re.DOTALL,
    )
    if not match:
        raise AssertionError(f"Missing shell step: {name}")
    return textwrap.dedent(match[1])


class ReleaseWorkflowTest(unittest.TestCase):
    def test_release_tag_validation(self):
        cases = [
            ("20110-2.1.10", "2.1.10"),
            ("20110-2.1.10-rc.1", "2.1.10-rc.1"),
            ("20109-2.1.10", None),
            ("main", None),
            ("20110-2.1.10;touch injected", None),
        ]
        for tag, version in cases:
            with self.subTest(tag=tag), tempfile.TemporaryDirectory() as directory:
                output = Path(directory) / "output"
                result = subprocess.run(
                    [BASH, "-e", "-o", "pipefail", "-c", shell_step("Validate release tag")],
                    env=dict(os.environ, RELEASE_TAG=tag, GITHUB_OUTPUT=output.as_posix()),
                    capture_output=True, text=True,
                )
                if version is None:
                    self.assertNotEqual(result.returncode, 0)
                else:
                    self.assertEqual(result.returncode, 0, result.stderr)
                    self.assertEqual(output.read_text(), f"version={version}\nrelease_tag={tag}\n")

    def test_new_draft_and_published_release_paths(self):
        mock = r'''
gh() {
  if [[ "$1 $2" == 'release view' ]]; then
    [[ "$STATE" != missing ]] || return 1
    printf '%s\n' "$STATE"
  else
    printf '%s\n' "$*" >> "$CALLS"
  fi
}
'''
        for state in ("missing", "true", "false"):
            with self.subTest(state=state), tempfile.TemporaryDirectory() as directory:
                calls = Path(directory) / "calls"
                result = subprocess.run(
                    [BASH, "-e", "-o", "pipefail", "-c", mock + shell_step("Publish signed release")],
                    env=dict(os.environ, STATE=state, CALLS=calls.as_posix(), VERSION="2.1.10",
                             RELEASE_TAG="20110-2.1.10", APK_PATH="release.apk", IS_PRERELEASE="false"),
                    capture_output=True, text=True,
                )
                recorded = calls.read_text() if calls.exists() else ""
                if state == "false":
                    self.assertNotEqual(result.returncode, 0)
                    self.assertEqual(recorded, "")
                else:
                    self.assertEqual(result.returncode, 0, result.stderr)
                    self.assertIn("release create" if state == "missing" else "release upload", recorded)
                    self.assertIn("release.apk.sha256", recorded)
                    if state == "missing":
                        self.assertIn("--verify-tag --draft", recorded)
                    self.assertIn("release edit 20110-2.1.10 --draft=false --prerelease=false", recorded)


if __name__ == "__main__":
    unittest.main()
