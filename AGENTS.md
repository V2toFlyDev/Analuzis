# Analuzis — notas de desarrollo

App Android nativa (APK, Java + XML, **sin Gradle**) para comparar una tarifa
eléctrica con las ofertas publicadas por la CNMC, usando facturas en PDF/QR y el
autoconsumo de una instalación solar. Todo se guarda en el propio móvil
(`SharedPreferences` y almacenamiento privado de la app), sin nube ni cuentas del
desarrollador.

Este documento es documentación técnica pública. No contiene datos reales de
ningún usuario: los ejemplos y perfiles que aparecen son ficticios.

## Estado

- Versión estable: **1.1** (`versionCode` 13).
- La app compila con `bash tools/build.sh`; el APK de distribución no se guarda en
  el repositorio (se genera en `out/` y se conserva solo en local).
- La clave de firma (`tools/release.jks`) y su contraseña (`tools/.firma-pass`) no
  se versionan nunca.
- Fase 1 completada. El plan de Fase 2 (Google Play) y Fase 3 (PWA iOS) está en
  [ROADMAP.md](ROADMAP.md).
- La 1.1 actual no está preparada para Play: genera APK, usa `targetSdk 35`,
  mantiene `allowBackup=true` y no cifra las credenciales.
- La firma actual usa el alias `V2toFlyDev` y el certificado
  `CN=Analuzis, O=Analuzis, C=ES`. Las instalaciones firmadas con la clave anterior
  no se actualizan con esta firma: hace falta exportar/importar y desinstalar.
- La copia/restauración local ya está implementada en la versión 1.1:
  incluye preferencias, facturas, PDFs, curva de Datadis, meses del inversor y
  PVPC; excluye NIF, contraseña de Datadis y token de ESIOS.
- La detección de cambios de tarifa guarda sus comparaciones en
  `tariffChanges` (preferencias) y las muestra en «Cambios de tarifa».

## Cómo compilar

Requisitos: JDK, Android SDK con `build-tools` y `platforms/android-35`, y las
herramientas del proyecto.

```bash
cd Analuzis
bash tools/build.sh
```

- `bash tools/build.sh` genera la versión estable indicada en el manifiesto.
- `VERSION=testN bash tools/build.sh` genera una versión de prueba con otro
  `versionName` y el mismo `versionCode`.
- Para subir versión: `VERSION=1.1 VERSION_CODE=13 bash tools/build.sh`.
- La firma se toma de `tools/release.jks`; la contraseña, de
  `ANALUZIS_STORE_PASS` o de `tools/.firma-pass`. Si falta el keystore, el script
  crea uno nuevo con `CN=Analuzis, O=Analuzis, C=ES`; eso cambia la firma y obliga
  a desinstalar la app instalada.
- Instalación por ADB:
  `adb install -r out/Analuzis.apk`.
- Comprobar versión:
  `adb shell dumpsys package com.analizis.app | grep -i versionName`.

Regla de trabajo: ADB solo se usa para instalar y consultar la versión. No se
recorren carpetas del móvil ni del PC desde la app.

## Estructura

- `app/java/com/analizis/app/`: actividades y lógica.
  - `MainActivity`: pantalla principal, facturas, precios, placas, ayuda.
  - `ResultsActivity`: comparador CNMC, PVPC y detalle de ofertas.
  - `ManualActivity` / `ManualListActivity`: ofertas escritas a mano.
  - `BillData`: perfil, facturas, precios, reprecio y comparación.
  - `Invoice`, `TariffChange`, `History`, `PdfReader`, `PdfPrices`, `PdfQr`,
    `Pdfs`: facturas, comparación de tarifas, lectura de PDF, QR y copias locales.
  - `CnmcClient`, `Offer`, `Web`, `Censo`: comparador de la CNMC, fichas, webs y
    censo.
  - `Datadis`, `Curve`, `SolarMonth`: curva horaria y meses solares.
  - `Esios`, `Pvpc`: PVPC.
  - `Store`, `Fmt`, `Dialogs`.
  - `Backup`, `BackupActivity`: copia y restauración local en JSON, con selector
    de archivos del sistema y sin credenciales.
- `app/res/`: layouts, estilos, colores y recursos.
- `tools/build.sh`: compilación y firma sin Gradle.
- `tools/PdfCheck.java` y `tools/PriceCheck.java`: comprobaciones de escritorio
  con datos ficticios.
- `libs/core-3.5.3.jar`: ZXing core (Apache 2.0), solo se usa para decodificar QR.

## Reglas de cálculo

- «Tu tarifa real» usa **solo la última factura por fecha**, con ventana de 12
  meses o 365 días.
- La cifra del comparador es la **repreciada**: los precios vigentes de la última
  factura aplicados al año. No es el importe histórico pagado, que mezcla precios
  de cada mes. Si la diferencia entre ambas es relevante, se enseñan las dos.
- La compensación de excedentes se resta con la misma función en la base y en las
  ofertas. Solo se descuenta a las ofertas que compensan (o a las que tienen un
  precio manual de excedentes); una oferta que la CNMC marca como no
  compensadora no recibe descuento aunque su ficha mencione excedentes.
- Precios de energía por tramo (`pe1`/`pe2`/`pe3`): si falta un tramo, se usa el
  precio único. `pe` es el precio efectivo que reproduce la factura; `peNow` es el
  vigente hoy. El cuadre usa `pe`; el reprecio anual usa `peNow` si existe.
- `energyCostYear()` y `energyCostBill()` son los únicos sitios donde se cruzan
  kWh y precios.
- El excedente anual se calcula con el precio de la última factura por los kWh
  vertidos del año; el importe realmente cobrado se conserva solo como dato
  informativo.
- El cuadre de una factura usa «Total electricidad» más su IVA. Si no cuadra al
  céntimo, no se guardan los precios y se avisa.
- La curva horaria usa el reparto 2.0TD: P1 de 10–14 y 18–22; P2 de 8–10, 14–18 y
  22–24; P3 el resto y fines de semana. Los festivos entre semana cuentan como
  laborables. La curva se escala al consumo anual de las facturas.

## Entrada de datos

- `Fmt.parse()` acepta coma y punto como separador decimal en toda entrada manual.
  Si aparecen los dos, el último se interpreta como decimal y el otro como miles.
- Los campos numéricos usan `DigitsKeyListener` con `0123456789.,`.
- Las cifras de lectura llevan unidad. En las casillas de edición no se duplica la
  unidad en el valor si ya está en la etiqueta.
- No meter valores largos en `insideRow`: si el valor supera el ancho de la
  tarjeta, el rótulo puede quedarse sin espacio. Para cuentas largas se usa
  `insideNote` o una fila aparte.

## Pantallas y flujo

1. **Tu factura**: se añade por QR, PDF o a mano. Sin factura no hay comparación.
2. **Tu tarifa real**: precios de la última factura y enlaces a tramos y PVPC.
3. **Tus placas y excedentes**: autoconsumo, vertido, batería virtual y meses.
4. **Qué quieres en la oferta**: filtros de la lista final.

Abajo están el comparador, el listado manual, el censo y la ayuda `?`.

- El comparador pide las ofertas a la CNMC y las ordena por coste anual neto.
- El PVPC se añade como una oferta más cuando hay precios de ESIOS; su parte fija
  se valida antes de usarla.
- Las ofertas manuales se guardan en `SharedPreferences` y sustituyen a la oferta
  de la CNMC con la misma comercializadora y nombre. Si se borra la entrada
  manual, vuelve la automática.
- El listado manual usa las mismas tarjetas y píldoras que el comparador.
- La caché de la última comparativa permite enseñar una lista guardada si la CNMC
  falla.

## APIs externas

- **CNMC**: API pública del comparador. El cliente envía el perfil de consumo,
  potencia y código postal. El backend nuevo exige `perfilConsumo=13` y los campos
  del QR a cero; sin ellos puede devolver HTTP 500. Los kWh deben ser enteros.
  El detalle de una oferta se pide con `idOferta` una sola vez.
- **Datadis**: API privada con login NIF/contraseña. Se descarga la curva de los 12
  meses cerrados y no se puede repetir la misma consulta en 24 horas. El vertido
  de Datadis es el que se paga; el autoconsumo que no pasa por el contador no lo
  ve la distribuidora.
- **ESIOS**: precios del PVPC con un token personal. Se piden los indicadores 1001
  y 1739 de los 12 meses cerrados. Solo compara el término de energía.
- **Webs de comercializadoras**: se leen en el móvil para buscar el precio de
  excedentes cuando la ficha de la CNMC no lo dice. Si la web usa JavaScript o un
  PDF, puede no leerse; queda el campo manual.
- **Censo de la CNMC**: se lee solo al pulsar el enlace y se cachea 30 días.

La app no es oficial ni está afiliada a la CNMC, Datadis, ESIOS ni a ninguna
comercializadora. Antes de distribuirla públicamente hay que revisar las
condiciones de uso de esas APIs.

## Privacidad y seguridad

- Las facturas, PDFs, precios, curvas y ofertas manuales se guardan en el móvil.
- No hay analítica, publicidad ni servidor propio.
- El NIF/contraseña de Datadis y el token de ESIOS se guardan en las preferencias
  privadas de la app. Para Play (Fase 2) hay que cifrarlos con Android Keystore.
- `android:allowBackup="true"` permite que Android haga copia de seguridad de los
  datos; en Fase 2 hay que desactivarlo o excluir PDFs y credenciales.
- La app pide permiso de cámara solo para leer el QR de la factura.
- Para borrar los datos: borrar las facturas desde la app, «Borrar todo» o
  desinstalar. Los PDFs se guardan en `filesDir/pdfs` y se borran con la factura.

## Trampas conocidas

- **Diálogos recortados**: todo texto largo debe llevar `Dialogs.fill(...)` o
  `Dialogs.wideRow(...)`. Un `TextView` sin `LayoutParams` mide el texto en una
  línea y ensancha el diálogo.
- **Píldoras**: un `LinearLayout` horizontal no parte las píldoras; `addPills` las
  mide y las reparte en filas.
- **CNMC y CORS**: la API devuelve 403 si la petición lleva `Origin` de navegador.
  Una PWA no puede llamarla directamente; necesita un proxy propio.
- **Datadis**: la hora del intervalo es la de fin (`01:00` = 00:00–01:00). Las
  fechas de la API son `AAAA/MM`. No sustituir los meses con autoconsumo 0: el 0
  significa «no lo ve», no «no hay».
- **PDFs**: el lector propio extrae texto e imágenes, pero no es un visor general.
  Si un PDF no se lee, se puede meter la factura a mano.
- **Firma**: la clave actual (alias `V2toFlyDev`, certificado
  `CN=Analuzis`) no actualiza instalaciones firmadas con la clave anterior. Antes
  de sustituir una instalación hay que exportar/importar datos y desinstalar.
  Cambiar solo el alias no cambia la identidad; cambiar la clave o el certificado
  sí.
- **Versión de Android**: `targetSdk 35` ya no vale para nuevas apps en Google
  Play desde el 31/08/2026; hay que subir a 36 y probar edge-to-edge.

## Pruebas de escritorio

Los harness viven en `build/t` **después** de compilar, porque el build borra
`build/`. Ejemplo:

```bash
javac -encoding UTF-8 -source 11 -target 11 -nowarn -classpath "libs/*" \
  -d build/t app/java/com/analizis/app/Fmt.java build/t/HarnessFmt.java
java -classpath "libs/android.jar;build/t" HarnessFmt
```

El `org.json` de `android.jar` es un stub en escritorio: no usar `toJson/fromJson`
en un harness. No dejar logs del build dentro de `build/`.

## Publicación

El repositorio público no incluye APK, PDFs, keystore, contraseñas ni datos de
usuarios. `private/` está ignorado y contiene las copias de trabajo. Para Play
Store hacen falta, además: AAB, `targetSdk 36`, Play App Signing, política de
privacidad pública, Data safety, `allowBackup` controlado, credenciales cifradas,
modo demo para revisores y la prueba cerrada de 12 testers durante 14 días. Ver
[ROADMAP.md](ROADMAP.md). La clave nueva ya existe; la copia/restauración está
en la versión 1.1, pero falta validarla en la instalación nueva antes de
distribuir. La instalación anterior (1.6-copia) no se actualiza con la firma
nueva.

## Licencia

MIT. Ver `LICENSE`. ZXing core se distribuye bajo Apache 2.0; ver
`THIRD_PARTY_NOTICES.md`.
