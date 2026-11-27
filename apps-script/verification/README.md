# Verificación directa: Firebase + Google Apps Script

Esta es la integración de correo activa en Android. No requiere Node, un VPS ni Cloud Functions. Sustituye las instrucciones anteriores que usaban `/registration/*` en el servidor. No se ha publicado el Apps Script remoto ni enviado un correo real desde esta revisión.

## Actualizar tu proyecto existente

1. Abre el proyecto **de verificación de correo** de Google Apps Script con la cuenta remitente que ya usabas. Sustituye su código antiguo por estos tres archivos `.gs`:
   - `Code.gs`: genera, limita y valida los códigos y actualiza Firebase.
   - `EmailTemplate.gs`: conserva el logo y el diseño original del correo.
   - `FirebaseConfig.gs`: configuración pública ya copiada de `app/google-services.json`. No contiene credenciales administrativas.
   No dejes los antiguos `doGet`/`doPost` definidos en otros archivos del mismo proyecto. No pegues el script de recuperación en este proyecto.
2. En Configuración del proyecto activa **Mostrar el archivo de manifiesto appsscript.json** y sustituye su contenido por el archivo adjunto. Incluye permisos de envío, solicitudes HTTPS y Firebase Authentication (`identitytoolkit`). El token OAuth de la cuenta permanece exclusivamente en Apps Script.
3. Vincula el script a un **proyecto estándar de Google Cloud** con la API **Identity Toolkit** habilitada; puede ser el mismo proyecto de Firebase. La cuenta que ejecuta el script necesita `firebaseauth.users.get` y `firebaseauth.users.update` sobre el proyecto Firebase. Un rol personalizado con esas dos autorizaciones limita mejor el acceso que otorgar Owner. Si esa cuenta ya tiene permisos suficientes, no es necesario concederlos otra vez. No hacen falta archivos JSON de cuenta de servicio ni contraseñas de Gmail.
4. En **Propiedades del script**, añade `OTP_SECRET`. Su valor debe ser una clave aleatoria de 32 bytes en hexadecimal (64 caracteres). Puedes generarla en PowerShell en tu propio equipo:

   ```powershell
   $otpBytes = New-Object byte[] 32
   $otpGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
   try { $otpGenerator.GetBytes($otpBytes) } finally { $otpGenerator.Dispose() }
   ([BitConverter]::ToString($otpBytes)).Replace('-', '').ToLowerInvariant()
   [Array]::Clear($otpBytes, 0, $otpBytes.Length)
   ```

   Copia el resultado solamente a las propiedades del script. No uses la clave antigua, no lo envíes por chat ni lo incluyas en Android. No borres `OTP_SEQUENCE` durante el uso normal; cambiar `OTP_SECRET` invalida desafíos anteriores.
5. Ejecuta manualmente **`comprobarConfiguracion`** desde el editor y acepta los permisos de Google. Esta comprobación no envía correo ni modifica usuarios: comprueba el acceso de consulta a Firebase y al servicio de correo. El permiso de actualización se confirma en la prueba completa posterior.
6. En **Implementar → Gestionar implementaciones → Editar → Nueva versión**, actualiza la implementación de verificación existente. Usa **Ejecutar como yo** (la cuenta remitente) y acceso **Cualquier usuario** para que la APK pueda invocarla sin un inicio de sesión de Google. La aplicación valida cada operación; no expone un método genérico de administración ni acepta códigos elegidos por el cliente.
7. Copia la dirección `/exec`. `gradle.properties` ya contiene `emailScriptUrl` con la URL que figuraba en el código original. Si actualizas esa misma implementación, se conserva. Si Google entrega otra dirección, reemplaza únicamente ese valor y vuelve a ejecutar la app desde Android Studio. La dirección es pública y no necesita un token compartido dentro de la APK.

Si la clave API de `FirebaseConfig.gs` tiene restricciones exclusivamente para Android, la consulta desde Apps Script puede ser rechazada. Configura una clave API separada del mismo proyecto, limitada a Identity Toolkit y compatible con solicitudes del script, y úsala solo en ese archivo. La clave API identifica el proyecto; las operaciones administrativas requieren además el OAuth autorizado del propietario. No retires las restricciones de la clave Android para resolverlo.

## Prueba manual

- Correo nuevo controlado por ti: completar formulario → Verificar → recibir seis dígitos → introducirlos → indicador verde → Crear cuenta. Consultar que Firebase Authentication muestra `emailVerified=true`.
- Correo ya registrado: mensaje explícito, sin correo de alta. El propietario autenticado de una cuenta no verificada puede retomar un registro interrumpido.
- Código incorrecto: no se marca en verde. Cinco intentos agotan el desafío.
- Reenvío tras un minuto: el código anterior deja de funcionar. Caducidad: diez minutos.
- Si aparece «necesita actualizarse», la URL todavía devuelve el script antiguo o una página de acceso de Google. Si aparece «falta completar la configuración», revisar `OTP_SECRET`, el manifiesto y la configuración de Firebase.

## Alcance y límites

- Android llama directamente al Apps Script. Los códigos y capacidades se protegen con HMAC-SHA256 nativo, una semilla generada mediante CSPRNG y un contador persistente bajo bloqueo; no se usa `Math.random`. Se guardan hashes y límites en Script Properties, no códigos en claro. La capacidad temporal de la app nunca se guarda en preferencias.
- Solo después de verificar el código se permite actualizar `emailVerified`; además, Firebase valida el ID token del usuario y el script comprueba proyecto, UID y correo. No recibe contraseñas maestras ni de cuenta.
- No es un sistema de recuperación de bóveda. La recuperación de contraseña de cuenta sigue usando el enlace oficial de Firebase.
- Apps Script impone cuotas. Se limita a cinco solicitudes diarias por correo, diez por minuto y sesenta diarias en todo el script, incluyendo solicitudes rechazadas por correo registrado. Es una configuración conservadora para desarrollo. No se dispone de IP fiable del cliente; un atacante puede agotar la cuota global. Antes de exponerla ampliamente se necesita protección adicional contra abuso y pruebas de carga. No se prometen disponibilidad ni escalado ilimitados.
- No se contrató ningún servicio. Se mantienen las cuotas gratuitas y las restricciones propias de Firebase/Google Apps Script.
- **La autorización de escrituras de la bóveda permanece en el protocolo anterior de `server/`. Este cambio elimina la dependencia Node del correo, no resuelve todavía esa otra integración.** No confundir verificar el correo con poder completar todo el flujo criptográfico en el entorno actual.

## Validación ejecutada

`node --test apps-script/verification/verification.test.js`: nueve pruebas aprobadas con MailApp, Script Properties y Firebase simulados; no envían mensajes ni usan cuentas reales. Quedan pendientes la autorización en Google, la actualización del despliegue y la prueba de envío real en el teléfono.

Referencias oficiales: [Apps Script Web Apps](https://developers.google.com/apps-script/guides/web), [OAuth del script](https://developers.google.com/apps-script/reference/script/script-app#getOAuthToken()), [consulta de usuarios](https://docs.cloud.google.com/identity-platform/docs/reference/rest/v1/accounts/lookup), [actualización de usuarios](https://docs.cloud.google.com/identity-platform/docs/reference/rest/v1/projects.accounts/update), [cuotas](https://developers.google.com/apps-script/guides/services/quotas).
