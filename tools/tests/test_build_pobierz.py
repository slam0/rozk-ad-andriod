import json, os, subprocess, sys, tempfile, unittest

SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "build_pobierz.py")


class T(unittest.TestCase):
    def test_empty(self):
        with tempfile.TemporaryDirectory() as d:
            os.makedirs(os.path.join(d, "src"))
            subprocess.run([sys.executable, SCRIPT, os.path.join(d, "src"), os.path.join(d, "out"), "https://x/cydia"], check=True)
            self.assertFalse(os.path.exists(os.path.join(d, "out", "pobierz.json")))

    def test_files(self):
        with tempfile.TemporaryDirectory() as d:
            root = os.path.join(d, "pkg", "DEBIAN")
            os.makedirs(root)
            open(os.path.join(root, "control"), "w").write(
                "Package: a.b\nVersion: 2.0\nArchitecture: iphoneos-arm\nDescription: t\nMaintainer: m\n")
            os.makedirs(os.path.join(d, "src"))
            subprocess.run(["dpkg-deb", "-b", os.path.join(d, "pkg"), os.path.join(d, "src", "a.deb")], check=True, capture_output=True)
            open(os.path.join(d, "src", "Rozklad.ipa"), "wb").write(b"zip")
            out = os.path.join(d, "out")
            subprocess.run([sys.executable, SCRIPT, os.path.join(d, "src"), out, "https://x.github.io/cydia"], check=True)
            info = json.load(open(os.path.join(out, "pobierz.json")))
            self.assertEqual(info["version"], "2.0")
            self.assertTrue(info["ipa"])
            self.assertEqual(info["repo"], "https://x.github.io/cydia/")
            self.assertTrue(os.path.exists(os.path.join(out, "pobierz", "Rozklad.ipa")))
            self.assertTrue(os.path.exists(os.path.join(out, "pobierz", "Rozklad.deb")))

    def test_apk_only(self):
        with tempfile.TemporaryDirectory() as d:
            os.makedirs(os.path.join(d, "src"))
            os.makedirs(os.path.join(d, "apk"))
            open(os.path.join(d, "apk", "Rozklad.apk"), "wb").write(b"PK12345")
            open(os.path.join(d, "apk", "android-wersja.txt"), "w").write("1.0.0\n")
            out = os.path.join(d, "out")
            subprocess.run([sys.executable, SCRIPT, os.path.join(d, "src"), out, "https://x.github.io/cydia", os.path.join(d, "apk")], check=True)
            info = json.load(open(os.path.join(out, "pobierz.json")))
            self.assertTrue(info["apk"])
            self.assertFalse(info["ipa"])
            self.assertEqual(info["apk_version"], "1.0.0")
            self.assertEqual(info["apk_size"], 7)
            self.assertTrue(os.path.exists(os.path.join(out, "pobierz", "Rozklad.apk")))

    def test_apk_empty_folder_is_nothing(self):
        with tempfile.TemporaryDirectory() as d:
            os.makedirs(os.path.join(d, "src"))
            os.makedirs(os.path.join(d, "apk"))
            out = os.path.join(d, "out")
            subprocess.run([sys.executable, SCRIPT, os.path.join(d, "src"), out, "https://x/cydia", os.path.join(d, "apk")], check=True)
            self.assertFalse(os.path.exists(os.path.join(out, "pobierz.json")))


if __name__ == "__main__":
    unittest.main()
