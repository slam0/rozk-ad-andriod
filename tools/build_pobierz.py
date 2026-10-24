#!/usr/bin/env python3
"""Składa część strony do pobierania aplikacji.

Użycie:  python3 tools/build_pobierz.py <folder_z_plikami> <folder_strony> <adres_repo_cydii> [<folder_z_apk>]

<folder_z_plikami> - tu leżą pliki pobrane z wydania (Release): *.deb i *.ipa.
Tworzy w <folder_strony>:
  pobierz.json           - informacje o wersji (czyta je strona główna)
  pobierz/Rozklad.ipa    - stały adres pliku do pobrania w Safari
  pobierz/Rozklad.deb    - to samo dla pliku .deb
  pobierz/Rozklad.apk    - aplikacja na Androida (gdy w <folder_z_apk> leży plik *.apk)
Repozytorium Cydii jest osobne (patrz README); tu zapisujemy tylko jego adres.
Gdy nie ma żadnego pliku, nic nie tworzy i kończy się bez błędu.
"""
import glob, json, os, shutil, subprocess, sys, time


def main(src, out, repo_url, apk_src=None):
    debs = sorted(glob.glob(os.path.join(src, "*.deb")))
    ipas = sorted(glob.glob(os.path.join(src, "*.ipa")))
    apks = sorted(glob.glob(os.path.join(apk_src, "*.apk"))) if apk_src else []
    if not debs and not ipas and not apks:
        print("Brak plików w wydaniu - strona będzie bez pobierania aplikacji.")
        return 0
    if not repo_url.endswith("/"):
        repo_url += "/"
    os.makedirs(os.path.join(out, "pobierz"), exist_ok=True)
    info = {"version": "", "size": 0, "ipa": False, "date": time.strftime("%Y-%m-%d", time.gmtime()),
            "repo": repo_url}
    if debs:
        ctrl = subprocess.run(["dpkg-deb", "-f", debs[-1]], check=True, capture_output=True).stdout.decode("utf-8")
        for line in ctrl.split("\n"):
            if line.startswith("Version:"):
                info["version"] = line.split(":", 1)[1].strip()
        shutil.copy(debs[-1], os.path.join(out, "pobierz", "Rozklad.deb"))
        info["size"] = os.path.getsize(debs[-1])
    if ipas:
        shutil.copy(ipas[-1], os.path.join(out, "pobierz", "Rozklad.ipa"))
        info["ipa"] = True
        info["ipa_size"] = os.path.getsize(ipas[-1])
        if not info["size"]:
            info["size"] = info["ipa_size"]
    if apks:
        shutil.copy(apks[-1], os.path.join(out, "pobierz", "Rozklad.apk"))
        info["apk"] = True
        info["apk_size"] = os.path.getsize(apks[-1])
        info["apk_version"] = ""
        vf = os.path.join(apk_src, "android-wersja.txt")
        if os.path.exists(vf):
            info["apk_version"] = open(vf, encoding="utf-8").read().strip()
    with open(os.path.join(out, "pobierz.json"), "w", encoding="utf-8") as f:
        json.dump(info, f, ensure_ascii=False)
    print("Pobieranie:", info)
    return 0


if __name__ == "__main__":
    if len(sys.argv) not in (4, 5):
        print(__doc__)
        sys.exit(2)
    sys.exit(main(*sys.argv[1:]))
