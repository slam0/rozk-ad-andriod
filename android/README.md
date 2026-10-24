# Rozkład na Androida (wersja próbna)

Ta sama aplikacja co na iOS 6, napisana od nowa w Javie: offline'owy rozkład jazdy
Warszawy (autobusy, tramwaje, pociągi, metro), duży „bilet” z najbliższym odjazdem,
odliczanie, ulubione, przypomnienia o odjeździe, 4 motywy, język polski / angielski / rosyjski.
Wygląd jak na iOS 6 (skóra, papier, czerwone paski). Wymaga **Androida 5.0 lub nowszego**.

> Aplikacja **nie była jeszcze sprawdzana na prawdziwym telefonie** (kod przeszedł kompilację i testy
> logiki na komputerze). Jeśli coś nie działa, wklej tu błąd lub zrzut ekranu.

## Jak zbudować plik .apk (bez komputera z Androidem Studio)

1. Wgraj zawartość tej paczki do repozytorium `slam0/Rozklad` (folder `android/` i pliki w `.github/`, `site/`, `tools/`).
2. Wejdź w **Actions → Zbuduj aplikację na Androida → Run workflow**.
3. Po kilku minutach plik `Rozklad.apk` jest w **Releases** (wydanie `android-najnowsza`)
   i na dole strony przebiegu (**Artifacts → rozklad-apk**). Strona z rozkładem po chwili
   sama pokaże przycisk **Pobierz na Androida (.apk)**.
4. Jeśli budowanie się nie uda: otwórz nieudany krok, skopiuj ostatnie ~50 linii logu i wklej do Claude.

## Instalacja na telefonie

Otwórz stronę `https://slam0.github.io/Rozklad/` w przeglądarce telefonu, dotknij **Pobierz na Androida (.apk)**,
otwórz pobrany plik. Android zapyta o zgodę na instalację z nieznanego źródła – zezwól dla tej przeglądarki.
Nowsze wersje instalują się na starszych bez odinstalowywania (stały klucz podpisu w `app/rozklad.keystore`).

## Pierwsze uruchomienie

1. Na stronie kliknij **Kreator przystanków**, wybierz swoje przystanki i dotknij **Otwórz w aplikacji Rozkład**.
2. Aplikacja zapyta „Pobrać rozkład?” – dotknij **Pobierz**. Dalej działa bez internetu.
3. Można też wczytać plik `rozklad.json` z telefonu (zakładka z Ustawieniami / przycisk **Wczytaj**).

## Jak to jest zbudowane

* `app/src/main/java/com/nazar0vsky/rozklad/` – kod. Czysta logika (bez Androida): `Json`, `Scan`, `Core`, `Lang`,
  `ImportReq`, `Assembler`, `Dataset`, `Schedule`, `Importer`. Ekrany i widoki: `MainActivity`, `*Screen`, `Skin`, `Widgets`, `Rows`.
* Cały wygląd jest rysowany w kodzie (bez plików układu XML), tekstury w `app/src/main/assets/`.
* `tests/` – testy logiki; szybkie uruchomisz tak: `cd android && sh tests/run_basic.sh` (potrzebna Java i Python).
* Przypomnienia używają systemowego budzika (`AlarmManager`). Na Androidzie 12+ może być potrzebna zgoda
  „Alarmy i przypomnienia”, a na 13+ zgoda na powiadomienia (aplikacja o nią zapyta).
* Format danych i link `rozklad://import?...` są takie same jak w wersji na iOS (patrz główny README).
