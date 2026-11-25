# Analuzis — plan de fases

Última actualización: 25 de noviembre de 2026.

Documento público. No contiene datos personales, credenciales ni claves
privadas.

## Fase 1 — Repositorio público y limpieza

**Estado: completada.**

- Repositorio público: <https://github.com/V2toFlyDev/Analuzis>.
- Historia limpia, sin APK, PDFs, keystore, contraseñas ni datos de usuarios.
- Documentación pública: `README.md`, `PRIVACY.md`, `LICENSE` y
  `THIRD_PARTY_NOTICES.md`.
- La clave de firma y las copias de trabajo siguen en local, ignoradas por Git.
- La clave de firma actual usa el alias `V2toFlyDev` y el certificado
  `CN=Analuzis, O=Analuzis, C=ES`.
- La clave anterior se conserva como copia privada. **La firma nueva no puede
  actualizar una instalación firmada con la clave anterior**: antes de sustituir
  la app instalada hay que exportar los datos, desinstalar e importarlos. La
  copia/restauración ya está implementada; falta validar la restauración en la
  instalación nueva.

## Fase 2 — Google Play

**Estado: siguiente. Todavía no hay nada publicado en Play Store.**

Objetivo: publicar una versión estable en Google Play sin perder los datos de las
instalaciones actuales y sin publicar claves ni datos personales.

### Orden de trabajo

1. **Exportar e importar datos.** Ya implementado en la build de prueba: JSON
   versionado con perfil, facturas y sus datos, precios, placas, excedentes,
   ofertas manuales, curva de Datadis, meses del inversor, PVPC y PDFs. No
   incluye credenciales de Datadis ni el token de ESIOS: se vuelven a introducir.
   Falta probarlo en la instalación nueva y validar la restauración. La
   exportación es también la entrada prevista para la Fase 3.
2. **Firma y Play App Signing.** La clave nueva ya existe y se usará como clave de
   subida. Decidir si Play App Signing genera la clave final o si se registra la
   actual. No publicar nunca el keystore ni la contraseña.
3. **AAB.** Play exige Android App Bundle, no un APK. Elegir Gradle o `bundletool`
   y mantener el APK como artefacto local de pruebas. El build actual solo genera
   APK.
4. **Android moderno.** Subir a `targetSdk 36` y `compileSdk 36`; probar
   edge-to-edge. El build actual usa `targetSdk 35`, que ya no sirve para nuevas
   apps en Play desde el 31/08/2026.
5. **Privacidad y seguridad.**
   - Poner `allowBackup="false"` o reglas de extracción que excluyan facturas,
     PDFs y credenciales.
   - Cifrar el NIF/contraseña de Datadis y el token de ESIOS con Android Keystore.
   - Publicar `PRIVACY.md` en una URL accesible desde Play.
   - Rellenar Data safety de forma coherente: no hay servidor del desarrollador,
     pero sí llamadas directas a CNMC, Datadis, ESIOS y webs de comercializadoras.
   - Revisar las condiciones de uso de las APIs antes de distribuir.
6. **Modo demo para revisores.** Incluir un perfil y una factura ficticios, sin
   datos reales, para que la app se pueda probar sin cuenta ni factura personal.
7. **Ficha y prueba cerrada.** Textos, icono, capturas, clasificación de
   contenido y política de privacidad. Para cuentas nuevas de desarrollador,
   completar la prueba cerrada con 12 testadores durante 14 días.
8. **Control de calidad.** Instalación limpia, exportación/importación,
   actualización desde una instalación compatible, cámara/QR, PDF, comparador,
   PVPC, Datadis, modo offline y borrado de datos. La actualización desde la
   instalación anterior (1.6-copia) con la firma antigua no es posible sin
   desinstalar; probar el recorrido de exportar, desinstalar, instalar e importar.
9. **Versión.** 1.1 con `versionCode` 13. El nombre visible se reinicia, pero el
   código sigue siendo mayor que el 11 anterior para no cerrar futuras
   actualizaciones.

### Criterios de aceptación

- AAB firmado y aceptado por Play Console.
- Política de privacidad y Data safety revisados.
- La app se instala desde cero y el recorrido de exportar/importar conserva los
  datos.
- Prueba cerrada completada y revisión de la ficha superada.
- Ningún APK, keystore, contraseña, factura o dato personal en el repositorio.

### Decisiones pendientes

- Gradle frente a `bundletool` para generar el AAB.
- Dónde alojar la política de privacidad pública.
- Clave de subida y configuración concreta de Play App Signing.
- Si la PWA reutiliza los PDFs de la copia o solo los datos estructurados.
- Textos, capturas y clasificación de la ficha.

## Fase 3 — PWA para iOS

**Estado: posterior a Play. No empezada.**

Objetivo: ofrecer una experiencia utilizable en iPhone/iPad sin depender de una
app nativa ni de publicar en la App Store.

### Opción recomendada: cliente acompañante

Una PWA estática que importe el JSON exportado por Android y permita consultar el
perfil, las facturas, los precios y las ofertas guardadas. No necesita servidor,
cuentas ni credenciales.

- Se instala desde Safari con «Añadir a pantalla de inicio».
- Funciona offline con los datos importados.
- No llama directamente a la CNMC; no hay que sortear CORS ni el 403.
- No envía datos al desarrollador.
- El formato de exportación debe estar versionado y validado al importar.

### Opción completa

Una PWA que consulte la CNMC en vivo necesita un proxy propio: la API devuelve
403 cuando la petición lleva `Origin` de navegador. También habría que decidir qué
hacer con Datadis y ESIOS, que permiten CORS pero cuyas credenciales no deberían
quedar expuestas en el navegador.

Esta opción exige backend, gestión de secretos, límites de uso, política de
privacidad nueva y revisar las condiciones de las APIs. No es la primera opción
mientras el cliente acompañante cubra el caso de uso.

### Criterios de aceptación

- La PWA se instala en iOS y abre offline.
- Importa el JSON de la versión Android actual sin perder datos.
- No hay credenciales ni datos personales en el servidor del desarrollador.
- Si se implementa la opción completa, el proxy está autenticado, limitado y
  documentado, y la política de privacidad refleja el nuevo tratamiento.

### Decisiones pendientes

- Cliente acompañante frente a PWA completa.
- Alojamiento estático y dominio.
- Esquema exacto del JSON y política de versiones.
- Si la PWA permite editar datos o solo consultarlos.
- Si se incluyen PDFs o solo datos estructurados.

## Dependencias entre fases

- La exportación/importación de Fase 2 es la entrada de Fase 3.
- No distribuir la nueva firma hasta que exista una forma de conservar los datos
  de la instalación anterior.
- La Fase 3 no debe introducir un servidor propio sin actualizar la política de
  privacidad y la información de Data safety.
