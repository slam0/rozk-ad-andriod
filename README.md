# Rozkład – aplikacja na iOS 6 (wersja 0.3)

Offline'owy rozkład jazdy na iPhone'a z jailbreakiem i iOS 6.x. Cztery zakładki
na dolnym pasku: **Autobus, Tramwaj, Pociąg, Metro**. W każdej: duży „bilet”
z najbliższym odjazdem, odliczanie („za 5 minut”), kolejne odjazdy i ruchomy
pasek z odliczaniem do końca ważności rozkładu. Wszystko liczy się według
zegara telefonu i działa bez internetu.

## Wersja na Androida (próbna)

W folderze `android/` jest ta sama aplikacja napisana w Javie, dla Androida 5.0 i nowszych. Buduje ją osobny
workflow **Zbuduj aplikację na Androida** (`.github/workflows/android.yml`, uruchamiany ręcznie przez *Run workflow*).
Plik `Rozklad.apk` trafia do wydania `android-najnowsza` (osobnego od wydania `najnowsza` z aplikacją na iOS)
i pojawia się na stronie jako przycisk **Pobierz na Androida (.apk)**. Szczegóły: `android/README.md`.

## Najprościej: strona z wyborem przystanków (GitHub Pages)

Strona działa na telefonie, w Safari na iOS 6. Wybierasz na niej przystanki i dotykasz
**„Otwórz w aplikacji Rozkład”** – aplikacja sama pobiera rozkład tych przystanków.
Nie potrzeba komputera, kabla ani Wi-Fi do komputera.

Jak to działa: duży plik `warsaw.zip` jest za ciężki dla telefonu, więc przetwarza go
GitHub (workflow **Strona z rozkładem**, plik `.github/workflows/strona.yml`). Skrypt
`tools/build_data.py` dzieli go na małe pliki (jeden na słupek przystankowy) i publikuje je razem ze stroną
z folderu `site/`. Telefon pobiera tylko listę przystanków (do wyszukiwania) i pliki wybranych słupków.
Workflow uruchamia się sam co poniedziałek i pobiera świeże dane, więc rozkład na stronie
jest zawsze aktualny.

Włączenie (raz): w repozytorium **Settings → Pages → Source: GitHub Actions**, potem
**Actions → Strona z rozkładem → Run workflow**. Adres strony to
`https://<twoja-nazwa>.github.io/<nazwa-repozytorium>/`. Repozytorium musi być publiczne
(na darmowym koncie). Jeśli GitHub nie może pobrać `warsaw.zip` z mkuran.pl, workflow użyje
pliku `dane/warsaw.zip` z repozytorium (dodaj go przez `git`, bo przeglądarka GitHuba przyjmuje
pliki do ok. 25 MB).

Format plików na stronie (dla ciekawych):

* `index.json` – `{v, gen, end, g:[[nazwa, klucz_wyszukiwania, maska, lat, lon, [[numer, kod_słupka, maska], ...]], ...]}`
* `d/<v>/svc.json` – `{gen, end, svc:[kalendarze]}` (kalendarze tak jak w `rozklad.json`)
* `d/<v>/s/<numer>.json` – `{n, c, g:[[linia, kierunek, numer_kalendarza, rodzaj, [minuty...]], ...]}`
* link do aplikacji: `rozklad://import?u=<adres strony>&v=<wersja>&p=<numer>.<maska>,...`
  (maska: autobus 1, tramwaj 2, pociąg 4, metro 8)

Aplikacja składa z tego zwykły `rozklad.json` (kod w `rz_import.c`, testy w `tests/` i `tools/tests/`).
Na iOS 6 pobieranie przez HTTPS wymaga poprawki SSL/TLS z Cydii.

Poniżej starsze sposoby (generator na komputerze, Wi-Fi, iTunes) – nadal działają.

## Jak płyną dane

1. Na komputerze otwierasz **generator-rozkladu.html**, wskazujesz `warsaw.zip`,
   wybierasz przystanki i klikasz **„Pobierz rozklad.json dla aplikacji”**.
2. Plik `rozklad.json` kopiujesz na telefon do folderu
   `/var/mobile/Documents/Rozklady/` (iFile, Filza albo `scp`).
3. W aplikacji dotykasz **Odśwież**. Aplikacja czyta wszystkie pliki `.json`
   z tego folderu, więc możesz mieć np. osobny plik dla pociągów. Stary plik
   po prostu usuń.

## Wysyłanie rozkładu przez Wi-Fi (najprościej)

1. W aplikacji dotknij **Wczytaj** → **Odbierz plik przez Wi-Fi**. Telefon pokaże swój adres,
   np. `192.168.0.12:8080`, i będzie czekał na plik. Nie zamykaj tego okna.
2. W generatorze (komputer w tej samej sieci Wi-Fi) w kroku 4 wpisz ten adres i kliknij
   **Utwórz rozklad.json**. Plik zostanie pobrany i od razu wysłany na telefon.
   Możesz też użyć przycisku **Wyślij na telefon** pod spodem.
3. Telefon zapisze plik, wczyta rozkład i pokaże „Odebrano rozkład”. Dotknij **Gotowe**.

Zamiast generatora możesz otworzyć adres telefonu w przeglądarce na komputerze
(np. `http://192.168.0.12:8080`) i wybrać plik `rozklad.json` na stronie, którą pokaże telefon.
Generator otwieraj z pliku na komputerze, nie ze strony w internecie (https), bo przeglądarka
może wtedy zablokować wysyłkę. Serwer działa tylko, gdy okno „Odbierz plik przez Wi-Fi”
jest otwarte, i przyjmuje tylko poprawny `rozklad.json`.

## Wybór przystanków w aplikacji

W każdej zakładce (Autobus, Tramwaj, Pociąg, Metro) po lewej stronie paska jest
przycisk **Przystanki**. Otwiera okno z listą wszystkich przystanków (słupków)
z wczytanego `rozklad.json`, np. „Centrum 01”, „Centrum 02”, „Rakowska 01”.
Zaznaczasz ptaszkiem te, które chcesz widzieć, i dotykasz **Gotowe**.
Przycisk **Wszystkie / Żadne** zaznacza lub czyści całą listę.

- Gdy wchodzisz do zakładki pierwszy raz po wczytaniu nowego pliku, okno
  wyskakuje samo.
- Wybór jest pamiętany osobno dla każdej zakładki, także po wyłączeniu aplikacji.
- Każdy słupek to osobna pozycja (generator zapisuje je osobno, razem z numerem
  słupka). Starszy `rozklad.json`, w którym słupki są połączone, też działa,
  ale wtedy na liście będzie jedna pozycja na cały przystanek.

Chcesz tylko sprawdzić, czy aplikacja działa? W folderze `przyklad/` jest
`rozklad.json` z wymyślonymi danymi (nazwy zaczynają się od „PRZYKŁAD”).

## Ulubione, odliczanie i ustawienia (wersja 1.1)

- **Lista i ekran przystanku**: każda zakładka (Autobus, Tramwaj, Pociąg, Metro) pokazuje
  na cały ekran listę wybranych przystanków. Przy każdym widać najbliższy odjazd
  („175 · Lotnisko Chopina · za 4 minuty”). Dotknięcie przystanku otwiera jego ekran z biletem
  najbliższego odjazdu i listą kolejnych.
- **Ulubione**: gwiazdka po prawej stronie wiersza (albo w prawym górnym rogu ekranu
  przystanku). Ulubione trafiają do sekcji „Ulubione” na górze listy.
- **Przypomnienie**: na ekranie przystanku dotknij odjazdu i wybierz „5, 10 albo 15 minut przed”. Telefon zadzwoni nawet przy wyłączonej aplikacji. Wszystkie przypomnienia anulujesz w Ustawieniach.
- **Odliczanie**: na „bilecie” z najbliższym odjazdem napis zmienia się co sekundę
  (np. „za 4 min 20 s”). Odjazdy za 5 minut lub mniej są czerwone.
- **Ustawienia** (piąta zakładka, zębatka):
  - **Motyw**: Skóra (jak Znajomi), Czerwony (czerwone paski na szarym lnie z Centrum powiadomień), Klasyczny (jak Cydia), Grafit (ciemne paski na szarym lnie).
  - **Odjazdów na ekranie przystanku**: dotknięcie zmienia 10 → 20 → 30.
  - **Dane**: ile przystanków i do kiedy ważny rozkład, przycisk do strony z rozkładem,
    usuwanie przeterminowanych albo wszystkich rozkładów, czyszczenie ulubionych.
  - **O aplikacji**: wersja, autor, adres strony.

## Czego jeszcze nie ma (wersja 0.1)

- Automatycznego pobierania rozkładu przez Wi-Fi i wyboru przystanków w samej
  aplikacji (to następny etap).
- Sprawdzenia na prawdziwym urządzeniu. Kod napisałem bez możliwości
  uruchomienia go na iOS 6, więc **pierwsze uruchomienie jest testem**.
  Logika odjazdów (`rz_core.c`) ma testy: `clang rz_core.c tests/core_test.c`.

---

## Budowanie: wybierz jedną z dróg

> **Najpierw przeczytaj.** Do zbudowania aplikacji dla iOS 6 trzeba skompilować
> **32-bitowy kod (armv7)**. Nowe Xcode, które idzie z macOS 27, usunęło stary
> linker (ld64), którego Apple używało do takich programów. Nie wiem, czy nowy
> linker potrafi zlinkować armv7, więc **droga A może nie zadziałać**.
> Droga B (GitHub) używa Linuxa z własnym zestawem narzędzi i nie wymaga
> instalowania niczego na Macu. Dlatego **zalecam zacząć od drogi B**.

### Droga B (zalecana): GitHub zbuduje to za Ciebie

Potrzebujesz tylko darmowego konta na github.com. Żadnego Terminala.

1. Zaloguj się na github.com → **New repository**. Nazwa np. `rozklad-app`,
   ustaw **Private**, kliknij **Create repository**.
2. Na stronie nowego repozytorium kliknij **uploading an existing file**.
3. Na Macu otwórz Findera, wejdź do rozpakowanego folderu `rozklad-app`
   i naciśnij **Cmd + Shift + .** (kropka), żeby pokazać ukryte pliki
   (potrzebny jest ukryty folder `.github`). Zaznacz **całą zawartość**
   (Cmd + A) i przeciągnij do okna przeglądarki. Poczekaj, aż się wgra,
   i kliknij **Commit changes**.
4. Sprawdź, czy w repozytorium widać `.github/workflows/build.yml`. Jeśli nie:
   **Add file → Create new file**, w polu nazwy wpisz
   `.github/workflows/build.yml` i wklej zawartość tego pliku z komputera.
5. Wejdź w zakładkę **Actions** → **Zbuduj aplikację** → **Run workflow**
   → zielony przycisk. Budowanie trwa kilka–kilkanaście minut.
6. Gdy przebieg będzie zielony, otwórz go i na dole w sekcji **Artifacts**
   pobierz **rozklad-deb**. W środku jest plik `.deb` (rozpakuj zip).
7. Jeśli przebieg jest czerwony: otwórz nieudany krok, skopiuj ostatnie
   ~30 linijek i wklej mi je. Poprawię kod albo konfigurację.

### Droga A: budowanie na Macu (M1, macOS 27)

Wszystkie polecenia wklejasz do Terminala (Cmd + Spacja, wpisz „Terminal”).

1. **Xcode** z App Store (pełny program, ok. 15–25 GB; same „Command Line
   Tools” nie wystarczą dla Theosa). Uruchom go raz, zaakceptuj licencję.
2. **Homebrew** (menedżer programów) i narzędzia:
   ```
   /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
   ```
   Na końcu instalator wypisze dwa polecenia „Next steps”. Wklej je.
   Potem:
   ```
   brew install ldid xz dpkg make
   ```
3. **Theos**:
   ```
   bash -c "$(curl -fsSL https://raw.githubusercontent.com/theos/theos/master/bin/install-theos)"
   echo 'export THEOS=~/theos' >> ~/.zprofile
   source ~/.zprofile
   ```
4. **SDK iOS.** Sprawdź, co ma Theos: `ls $THEOS/sdks`. Skrypt wybierze
   najstarsze SDK z zakresu 6.1–10.3 (starsze niż iOS 11 obsługują armv7).
   Jeśli nie ma żadnego, potrzebny jest `iPhoneOS6.1.sdk` z Xcode 4.6.3:
   pobierz go ze strony developer.apple.com/download/all (wystarczy darmowe
   Apple ID), otwórz plik `.dmg` i skopiuj
   `Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform/Developer/SDKs/iPhoneOS6.1.sdk`
   do folderu `~/theos/sdks/`.
5. **Zbuduj** (w folderze projektu):
   ```
   cd ~/Downloads/rozklad-app
   ./build.sh
   ```
   Gotowy plik pojawi się w `packages/` (`com.nazar0vsky.rozklad_0.2.0_iphoneos-arm.deb`).
   Jeśli skrypt zatrzyma się na błędzie z `ld` (linker) lub o nieobsługiwanej
   architekturze armv7, to właśnie opisany wyżej problem. Przejdź na drogę B.

---

## Dwa rodzaje paczek

Przebieg na GitHubie tworzy dwa pliki do pobrania (sekcja **Artifacts**):

- **rozklad-deb**: pakiet `.deb`. Instalujesz go w iFile/Filza lub przez SSH. Aplikacja
  trafia do systemowego folderu `/Applications`. Dane wrzucasz do
  `/var/mobile/Documents/Rozklady/`.
- **rozklad-ipa**: plik `.ipa` do instalacji jak zwykłej aplikacji (iTunes albo
  `ideviceinstaller`). Na telefonie musi być **AppSync Unified** z Cydii, bo plik nie jest
  podpisany przez Apple. Aplikacja ma włączone udostępnianie plików, więc w iTunes
  (zakładka Aplikacje → Udostępnianie plików → Rozkład) przeciągasz do niej `rozklad.json`.
  Plik może leżeć w głównym folderze lub w `Rozklady`; w głównym musi się nazywać
  `rozklad*.json`.
  Ta paczka nie została jeszcze sprawdzona na telefonie.

## Instalacja na telefonie

**Najprościej:** wyślij plik `.deb` na telefon (np. mailem, przez iFile/Filza
albo `scp`), otwórz go w **iFile** lub **Filza** i wybierz **Install** (instalator
dpkg). Potem zrestartuj SpringBoard (respring) – ikona **Rozkład** pojawi się
na ekranie. Pamiętaj o kopii zapasowej telefonu przed pierwszą instalacją.

**Przez SSH (z Maca):** nowy Mac odmawia połączeń ze starym iOS, bo ten zna
tylko przestarzałe algorytmy. Dopisz do pliku `~/.ssh/config`:
```
Host iphone
  HostName ADRES_IP_TELEFONU
  User root
  KexAlgorithms +diffie-hellman-group14-sha1,diffie-hellman-group1-sha1
  HostKeyAlgorithms +ssh-rsa
  PubkeyAcceptedAlgorithms +ssh-rsa
  Ciphers +aes128-cbc,aes256-cbc
```
Potem:
```
scp packages/*.deb iphone:/var/root/
ssh iphone "dpkg -i /var/root/com.nazar0vsky.rozklad_*.deb && uicache"
```
(Domyślne hasło po jailbreaku to `alpine`. Zmień je poleceniem `passwd`.)

Dane (`rozklad.json`) kopiujesz tak samo:
```
ssh iphone "mkdir -p /var/mobile/Documents/Rozklady"
scp rozklad.json iphone:/var/mobile/Documents/Rozklady/
```

## Gdy coś nie działa

| Objaw | Co zrobić |
|---|---|
| Błąd przy budowaniu | Wklej mi ostatnie ~30 linijek wyniku. |
| Instalacja pakietu zgłasza błąd | Wklej mi komunikat. Stary dpkg czyta tylko kompresję gzip (ustawiona w `Makefile`). |
| Ikony „Rozkład” nie widać | Uruchom `uicache` (SSH) albo zrób respring. |
| Aplikacja zamyka się zaraz po starcie | Wklej mi plik z `/var/mobile/Library/Logs/CrashReporter/` (najnowszy z „Rozklad” w nazwie). |
| „Brak rozkładu” | Plik musi być w `/var/mobile/Documents/Rozklady/`, mieć końcówkę `.json` i pochodzić z generatora. Dotknij **Odśwież**. |
| Zakładka pusta („nie ma przystanków…”) | Plik ma przystanki innego rodzaju. Rodzaj bierze się z `route_type` linii w GTFS (autobus, tramwaj, kolej, metro). |

## Z czego składa się projekt

| Plik | Do czego |
|---|---|
| `rz_core.c`, `rz_core.h` | Logika: które kursy jadą danego dnia, najbliższe odjazdy, teksty „za 5 minut”. Przetestowana (`tests/`). |
| `RZSchedule.m` | Wczytywanie plików `.json` z folderu. |
| `RZTabController.m` | Jedna zakładka: lista wybranych przystanków z najbliższym odjazdem i ulubionymi, pasek z odliczaniem. |
| `RZStopController.m` | Ekran jednego przystanku: bilet z najbliższym odjazdem i kolejne odjazdy. |
| `RZViews.m` | Wygląd i motywy: znaczki linii, bilet, ruchomy pasek, ikony zakładek, paski, tła. |
| `RZPickerController.m` | Okno wyboru przystanków z ptaszkami. |
| `RZSettingsController.m` | Zakładka Ustawienia: motywy, dane, informacje o aplikacji. |
| `RZWifiController.m`, `rz_http.c`, `rz_http.h` | Odbieranie `rozklad.json` przez Wi-Fi (mały serwer HTTP). Test: `clang rz_http.c rz_core.c tests/http_test.c -lpthread`. |
| `AppDelegate.m`, `main.m` | Start aplikacji i dolny pasek zakładek. |
| `Makefile`, `control`, `Resources/`, `layout/` | Konfiguracja budowania i pakietu. W `Resources/` są też tekstury skóry i papieru (`nav_leather`, `tab_leather`, `tab_select`, `bg_paper`). |
| `build.sh`, `.github/workflows/build.yml` | Budowanie lokalnie (A) i na GitHubie (B). |
| `przyklad/rozklad.json` | Wymyślone dane do sprawdzenia aplikacji. |

## Dane i licencje

Rozkłady pochodzą z danych ZTM Warszawa (GTFS), udostępnianych m.in. przez mkuran.pl.
Przy publikowaniu aplikacji i generatora podaj to źródło. Aplikacja nie zbiera żadnych
danych o użytkowniku i nie łączy się z internetem. Rozkład może być nieaktualny, a pasek
u góry informuje, kiedy wygaśnie.

## Ekran powitalny

`Resources/Default*.png` to ekran startowy (logo z czerwonymi promieniami). Po uruchomieniu aplikacja dodatkowo przytrzymuje go ok. 1,4 s i płynnie wygasza (`AppDelegate.m`). Aby zmienić czas, edytuj `1.4` w `didFinishLaunchingWithOptions`.

## Strona główna i pobieranie

Adresy (po włączeniu GitHub Pages):

* `https://slam0.github.io/Rozklad/` – strona główna z pobieraniem aplikacji (`site/index.html`),
* `https://slam0.github.io/Rozklad/kreator/` – kreator przystanków (`site/kreator/index.html`),
* `https://slam0.github.io/Rozklad/pobierz/Rozklad.ipa` – stały adres pliku .ipa.

Workflow **Zbuduj aplikację** po zbudowaniu publikuje `.deb` i `.ipa` jako wydanie (Release) o stałej nazwie
`najnowsza`. Workflow **Strona z rozkładem** (uruchamia się sam po budowaniu) pobiera to wydanie, a
`tools/build_pobierz.py` składa z niego `pobierz.json` i pliki w `pobierz/`. Dane rozkładowe (`index.json`, `d/…`)
są w głównym folderze strony, więc aplikacja i linki `rozklad://import?u=…` działają jak wcześniej.

W ustawieniach repozytorium (Settings → Actions → General → Workflow permissions) musi być zaznaczone
**Read and write permissions** – inaczej publikacja wydania się nie uda.

### Repozytorium Cydii

Paczka `.deb` trafia do Cydii przez **osobne repozytorium** `slam0/cydia`
(adres `https://slam0.github.io/cydia/`), wspólne dla wszystkich projektów. Ono samo pobiera wydania `najnowsza`
z projektów wypisanych w pliku `projekty.txt` (nowy projekt = jedna linijka). Dla tego projektu nic więcej
nie trzeba robić: wystarczy, że każde zbudowanie publikuje wydanie `najnowsza` z plikiem `.deb`.
Gdy nazwiesz repozytorium inaczej, zmień `CYDIA_REPO_URL` w `.github/workflows/strona.yml`.
