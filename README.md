# Analuzis

**Analuzis** es una app Android nativa para entender y comparar tu factura de la
luz. Lee tus facturas en PDF o por QR, compara tu tarifa con las ofertas públicas
de la CNMC y tiene en cuenta tu autoconsumo y los excedentes vertidos a la red.

Es un proyecto personal, **no oficial**: no está afiliado a la CNMC, Datadis,
ESIOS ni a ninguna comercializadora eléctrica.

## Qué hace

- **Lee tus facturas**: importa PDFs, escanea el QR o mete los datos a mano.
  Comprueba que la factura cuadra al céntimo antes de guardar los precios.
- **Compara con la CNMC**: ordena las ofertas por coste anual con tu consumo,
  potencia, impuestos y excedentes.
- **Ofertas a mano**: crea una oferta con los precios de la web de una
  comercializadora y compárala con las demás.
- **PVPC**: añade el PVPC real de ESIOS cruzado con tu curva horaria.
- **Placas y excedentes**: lleva el autoconsumo, el vertido y el precio que te
  pagan; puede leer la curva horaria de Datadis.
- **Todo en el móvil**: facturas, precios y ofertas se guardan localmente. No hay
  cuentas, nube, analítica ni publicidad.

## Privacidad

La app no envía datos a ningún servidor del desarrollador. Para funcionar consulta
directamente servicios de terceros:

- **CNMC**, para las ofertas del comparador.
- **Datadis**, si decides conectar tu curva de consumo.
- **ESIOS**, si decides consultar el PVPC con tu token.
- Webs de comercializadoras, para buscar el precio de los excedentes.

Los detalles están en [PRIVACY.md](PRIVACY.md).

## Estado

- Versión estable: **1.6** (`versionCode` 11).
- App Android nativa, Java + XML, sin Gradle para el APK de pruebas.
- La APK de distribución **no se guarda en este repositorio**.
- **Fase 1 completada**: repositorio público y documentación.
- **Fase 2**: preparación de Google Play; todavía no publicada.
- **Fase 3**: PWA para iOS, posterior a Play.
- Las instalaciones de la 1.6 usan una firma anterior; las compilaciones nuevas
  usan una firma distinta y no las actualizan sin exportar/importar y desinstalar.

El detalle de las fases está en [ROADMAP.md](ROADMAP.md).

## Compilar

Necesitas JDK, Android SDK y las `build-tools` que usa el proyecto.

```bash
bash tools/build.sh
```

El APK se genera en `out/Analuzis.apk`. Para pruebas:

```bash
VERSION=testN bash tools/build.sh
```

La firma se toma de `tools/release.jks` y de `tools/.firma-pass`. **Ese keystore y
su contraseña no se incluyen en el repositorio**: si compilas desde cero se crea
una clave nueva y la firma no coincidirá con la de las versiones publicadas.

## Estructura

- `app/`: código Java, layouts y recursos Android.
- `tools/build.sh`: compilación, empaquetado y firma sin Gradle.
- `tools/*.java`: comprobaciones de escritorio con datos ficticios.
- `libs/core-3.5.3.jar`: ZXing core para leer QR (Apache 2.0).

Las notas técnicas para quien siga desarrollando están en [AGENTS.md](AGENTS.md).

## Limitaciones

- La app depende de APIs y webs de terceros que pueden cambiar o dejar de
  responder.
- La lectura de PDFs y de webs con JavaScript tiene límites; siempre se puede
  introducir la factura o el precio a mano.
- No es una app de asesoramiento financiero ni una herramienta oficial de
  comparación.
- Antes de usarla con datos reales, revisa que las cifras y los precios
  introducidos son correctos.

## Licencia

MIT. Ver [LICENSE](LICENSE). Las licencias de terceros están en
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
