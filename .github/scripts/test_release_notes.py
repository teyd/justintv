import unittest

from release_notes import release_notes


class ReleaseNotesTest(unittest.TestCase):
    def test_extracts_only_requested_version(self):
        text = "# Changelog\n\n## [0.2.0] — today\n\nNew features.\n\n## [0.1.0] — yesterday\n\nInitial release.\n\n[0.1.0]: https://example.com\n"
        self.assertEqual(release_notes(text, "0.2.0"), "New features.\n")
        self.assertEqual(release_notes(text, "0.1.0"), "Initial release.\n")

    def test_supports_release_please_linked_heading(self):
        text = "## [0.1.1](https://example.com/compare) (2026-10-06)\n\n### Bug Fixes\n\n* Reconnect chat.\n"
        self.assertEqual(release_notes(text, "0.1.1"), "### Bug Fixes\n\n* Reconnect chat.\n")

    def test_missing_version_fails(self):
        with self.assertRaises(ValueError):
            release_notes("## 0.1.1\n\nFixes.\n", "0.1.0")

    def test_empty_entry_fails(self):
        with self.assertRaises(ValueError):
            release_notes("## 0.1.0\n\n## 0.0.1\nOlder.\n", "0.1.0")


if __name__ == "__main__":
    unittest.main()
