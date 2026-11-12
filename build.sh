#!/usr/bin/env bash
# Compile l'APK sans Android Studio ni Gradle : aapt -> javac -> dx -> aapt -> zipalign -> apksigner.
# Dépendances (Debian/Ubuntu) :
#   sudo apt install openjdk-21-jdk-headless android-sdk-platform-23 aapt dalvik-exchange zipalign apksigner
#
# Signature (au choix) :
#   - un fichier keystore.properties (jamais versionné) :
#       KEYSTORE=ma-cle.jks
#       KEYSTORE_PASS=mot-de-passe
#       KEY_ALIAS=agenda
#   - ou les mêmes variables d'environnement ;
#   - sinon une clé de développement est créée dans debug.jks (non versionnée).
# Attention : Android n'installe une mise à jour que si elle est signée avec la même clé que la version en place.
set -euo pipefail
cd "$(dirname "$0")"

SDK_JAR=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
OUT=${OUT:-Agenda.apk}
[ -f keystore.properties ] && . ./keystore.properties
KEYSTORE=${KEYSTORE:-debug.jks}
KEYSTORE_PASS=${KEYSTORE_PASS:-android}
KEY_ALIAS=${KEY_ALIAS:-debug}

for t in aapt javac dalvik-exchange zipalign apksigner keytool; do
  command -v "$t" >/dev/null || { echo "Outil manquant : $t (voir l'en-tête de build.sh)" >&2; exit 1; }
done
[ -f "$SDK_JAR" ] || { echo "android.jar introuvable : $SDK_JAR (paquet android-sdk-platform-23, ou ANDROID_JAR=...)" >&2; exit 1; }

if [ ! -f "$KEYSTORE" ]; then
  if [ "$KEYSTORE" = debug.jks ]; then
    echo "== clé de développement (debug.jks)"
    keytool -genkeypair -keystore debug.jks -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" \
      -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 36500 \
      -dname "CN=Agenda manuscrit (debug), O=Dev, C=FR" >/dev/null 2>&1
  else
    echo "Clé de signature introuvable : $KEYSTORE" >&2
    exit 1
  fi
fi

rm -rf build/app
mkdir -p build/app/classes build/app/gen

echo "== ressources"
aapt package -f -m -J build/app/gen -M AndroidManifest.xml -S res -I "$SDK_JAR"

echo "== javac"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$SDK_JAR" -d build/app/classes \
  $(find src build/app/gen -name '*.java')

echo "== dex"
dalvik-exchange --dex --min-sdk-version=21 --output=build/app/classes.dex build/app/classes

echo "== apk"
aapt package -f -M AndroidManifest.xml -S res -I "$SDK_JAR" -F build/app/unsigned.apk
(cd build/app && aapt add unsigned.apk classes.dex >/dev/null)
zipalign -f -p 4 build/app/unsigned.apk build/app/aligned.apk
apksigner sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KEYSTORE_PASS" --key-pass "pass:$KEYSTORE_PASS" \
  --out "$OUT" build/app/aligned.apk
rm -f "$OUT.idsig"
apksigner verify --print-certs "$OUT" | head -2
ls -l "$OUT"
