# Licencias de terceros

Analuzis incluye o utiliza los siguientes componentes de terceros.

## ZXing core 3.5.3

- Archivo: `libs/core-3.5.3.jar`
- Proyecto: <https://github.com/zxing/zxing>
- Licencia: Apache License 2.0
- Copyright: ZXing authors

Se usa únicamente para decodificar el QR de las facturas. El texto completo de la
licencia está disponible en:
<https://www.apache.org/licenses/LICENSE-2.0>.

## Android SDK

El proyecto compila contra el Android SDK, pero no redistribuye `android.jar`:
`.gitignore` lo excluye y cada equipo debe usar el SDK instalado localmente.

## Datos y marcas

Los nombres de la CNMC, Datadis, ESIOS y las comercializadoras se usan solo para
describir los servicios que consulta la app. No implican afiliación ni patrocinio.
