# Política de privacidad de Analuzis

Última actualización: 25 de noviembre de 2026.

Analuzis es una aplicación Android que funciona en el propio dispositivo. El
desarrollador no opera servidores propios ni recibe tus facturas, tu consumo o tus
credenciales.

## Qué datos se guardan en el móvil

- Las facturas que importas: PDFs, datos del suministro, consumos, potencias,
  precios, importes e historial.
- El perfil de tu vivienda: código postal, potencia contratada, consumo anual y
  reparto por periodos.
- Tus placas: autoconsumo, energía vertida y producción mensual.
- Tus ofertas manuales y tus filtros.
- Si decides usar Datadis: NIF/NIE y contraseña, y la curva de consumo descargada.
- Si decides usar ESIOS: tu token personal y el resumen del PVPC.
- El precio de excedentes que hayas escrito a mano.

Estos datos se guardan en el almacenamiento privado de la app
(`SharedPreferences` y `filesDir`). No se envían al desarrollador.

## Qué se envía a terceros

Solo se consultan los servicios necesarios para las funciones que uses:

- **CNMC** (`comparador.cnmc.gob.es`): código postal, potencia, consumo y periodos
  para obtener las ofertas del comparador.
- **Datadis** (`datadis.es`): NIF/NIE y contraseña para iniciar sesión, y el
  identificador del suministro para descargar la curva. Datadis es un servicio de
  la distribuidora; su uso se rige por sus propias condiciones.
- **ESIOS** (`api.esios.ree.es`): tu token personal para consultar los precios del
  PVPC.
- **Webs de comercializadoras**: se descargan páginas públicas para buscar el
  precio de excedentes de una oferta.

Analuzis no controla lo que esos terceros hacen con los datos que les envías.
Consulta sus políticas de privacidad y condiciones de uso.

## Analítica, publicidad y cuentas

- No hay analítica.
- No hay publicidad.
- No hay cuentas de usuario ni registro.
- No se venden ni se comparten datos con anunciantes.

## Permisos

- **Internet**: necesario para consultar la CNMC, Datadis, ESIOS y webs de
  comercializadoras.
- **Cámara**: solo se usa mientras escaneas el QR de una factura. No se guardan
  fotos ni vídeo.

## Conservación y borrado

Los datos permanecen en el móvil hasta que los borras:

- Puedes borrar una factura desde el historial; su PDF también se elimina.
- «Borrar todo» elimina las facturas, precios, ofertas y datos asociados.
- Desinstalar la app elimina sus datos locales.
- Si Android tiene activada la copia de seguridad, puede conservar una copia de
  los datos de la app en la cuenta de Google del dispositivo. Puedes desactivarla
  en los ajustes de Android.

## Seguridad

Las comunicaciones con los servicios externos se hacen por HTTPS. Las
credenciales de Datadis y el token de ESIOS se guardan en el almacenamiento
privado de la app; no los envíes por chat ni los publiques.

## Menores

Analuzis no está dirigida a menores de edad y no recoge datos de menores de forma
consciente.

## Cambios

Esta política puede cambiar si la app añade funciones o servicios. La fecha de
actualización figura al principio.

## Contacto

Para dudas o para solicitar el borrado de datos, abre una incidencia en el
repositorio del proyecto:
<https://github.com/V2toFlyDev/Analuzis/issues>.

No incluyas en una incidencia datos personales, facturas, NIF, CUPS ni
credenciales.
