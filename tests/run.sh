#!/usr/bin/env bash
# Tests hors Android, sur la JVM (quelques classes d'Android sont remplacées par des bouchons dans tests/stubs).
# Usage : tests/run.sh            (rapide)
#         tests/run.sh 4000       (mesure fine de la reconnaissance des carrés, ~1 min)
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=build/tests
rm -rf "$OUT"
mkdir -p "$OUT"
S=src/fr/agenda/manuscrit
javac -nowarn -encoding UTF-8 -d "$OUT" \
  $(find tests/stubs tests/src -name '*.java') \
  $S/Days.java $S/Settings.java $S/CalEvents.java $S/PageStore.java $S/Edit.java $S/Streaks.java \
  $S/Item.java $S/Shapes.java $S/Page.java $S/Stroke.java $S/Obj.java $S/Geo.java
for t in ModelTests StreakTests CalTests SettingsTests; do
  java -cp "$OUT" fr.agenda.manuscrit.$t
done
java -cp "$OUT" fr.agenda.manuscrit.BoxBench "${1:-600}"
