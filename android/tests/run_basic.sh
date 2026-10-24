#!/bin/sh
# Szybkie testy logiki (bez Androida): rachunek odjazdów i komplet tłumaczeń.
# Uruchom z folderu android/:  sh tests/run_basic.sh
set -e
SRC=app/src/main/java/com/nazar0vsky/rozklad
OUT=$(mktemp -d)
javac -encoding UTF-8 -d "$OUT" $SRC/Json.java $SRC/Scan.java $SRC/Core.java $SRC/Lang.java $SRC/LangTable.java tests/CoreTest.java tests/LangAuditTest.java
python3 tests/lang_audit.py $SRC "$OUT/lang_used.json"
java -Dfile.encoding=UTF-8 -cp "$OUT" CoreTest
java -Dfile.encoding=UTF-8 -cp "$OUT" LangAuditTest "$OUT/lang_used.json"
rm -rf "$OUT"
