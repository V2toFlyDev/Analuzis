#!/usr/bin/env bash
# Compila el APK usando las herramientas del SDK de Android ya instaladas.
set -euo pipefail

cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-${LOCALAPPDATA:-$HOME/AppData/Local}/Android/Sdk}"
BT="${BT:-$SDK/build-tools/36.0.0}"
PLATFORM="$SDK/platforms/android-35/android.jar"

if [ ! -f "$PLATFORM" ]; then
  echo "No encuentro android.jar en $PLATFORM" >&2
  exit 1
fi

rm -rf build out
mkdir -p build/gen build/classes build/dex out libs
cp "$PLATFORM" libs/android.jar

echo "[1/6] compilando recursos"
"$BT/aapt2.exe" compile --dir app/res -o build/res.zip

# El versionName del manifiesto manda sobre --version-name, así que para desarrollo se
# genera una copia del manifiesto con el nombre de la versión pedida.
MANIFEST=app/AndroidManifest.xml
if [ -n "${VERSION:-}" ]; then
  sed 's/android:versionName="[^"]*"/android:versionName="'"$VERSION"'"/' \
    app/AndroidManifest.xml > build/AndroidManifest.xml
  MANIFEST=build/AndroidManifest.xml
fi

echo "[2/6] enlazando recursos y manifest"
"$BT/aapt2.exe" link \
  -o build/base.apk \
  -I libs/android.jar \
  --manifest "$MANIFEST" \
  --auto-add-overlay \
  --java build/gen \
  -R build/res.zip \
  --min-sdk-version 26 --target-sdk-version 35 \
  --version-code "${VERSION_CODE:-11}" --version-name "${VERSION:-1.6}"
# La 1.6 es la estable (versionCode 11). Para pruebas, el nombre cambia y el código no:
#   VERSION=test82 bash tools/build.sh
# y para subir la versión de verdad: VERSION=1.7 VERSION_CODE=12 bash tools/build.sh

echo "[3/6] compilando Java"
find app/java build/gen -name '*.java' > build/sources.txt
javac -encoding UTF-8 -source 11 -target 11 -nowarn \
  -classpath "libs/*" -d build/classes @build/sources.txt

echo "[4/6] generando dex"
jar cf build/classes.jar -C build/classes .
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 26 \
  --lib libs/android.jar --output build/dex \
  build/classes.jar libs/core-3.5.3.jar

echo "[5/6] empaquetando y alineando"
python tools/adddex.py build/base.apk build/dex/classes.dex build/unsigned.apk
"$BT/zipalign.exe" -f -p 4 build/unsigned.apk build/aligned.apk

echo "[6/6] firmando"
KS=tools/release.jks
ALIAS=david-dorado
# La contraseña NO está en este script: se toma de un fichero local ignorado por git
# (tools/.firma-pass) o de la variable de entorno ANALUZIS_STORE_PASS.
PASSFILE=tools/.firma-pass

if [ -z "${ANALUZIS_STORE_PASS:-}" ]; then
  if [ ! -f "$PASSFILE" ]; then
    echo "Falta la contraseña de firma." >&2
    echo "Crea $PASSFILE con la contraseña en una sola línea, o exporta ANALUZIS_STORE_PASS." >&2
    exit 1
  fi
  ANALUZIS_STORE_PASS="$(tr -d '\r\n' < "$PASSFILE")"
fi
export ANALUZIS_STORE_PASS
# apksigner lee la contraseña del entorno: no aparece en la línea de comandos.
KS_PASS="env:ANALUZIS_STORE_PASS"

if [ ! -f "$KS" ]; then
  echo "  creando clave de firma de Analuzis"
  keytool -genkeypair -keystore "$KS" -alias "$ALIAS" -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$ANALUZIS_STORE_PASS" -keypass "$ANALUZIS_STORE_PASS" \
    -dname "CN=Analuzis, O=Analuzis, C=ES"
fi

java -jar "$BT/lib/apksigner.jar" sign \
  --ks "$KS" --ks-pass "$KS_PASS" --key-pass "$KS_PASS" \
  --out out/Analuzis.apk build/aligned.apk

java -jar "$BT/lib/apksigner.jar" verify --print-certs out/Analuzis.apk | head -4
ls -lh out/Analuzis.apk
echo "OK"
