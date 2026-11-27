# Verificación por código de correo

> **Integración anterior.** Android ahora usa directamente Firebase y Google Apps Script mediante `EMAIL_SCRIPT_URL`; no requiere este servidor para verificar correos. Sigue las instrucciones vigentes en [apps-script/verification/README.md](../apps-script/verification/README.md). El contenido siguiente se conserva como documentación del adaptador Node anterior.

Flujo restaurado: completar formulario → Verificar → correo con seis dígitos → menú `dialog_verify` → servidor acepta código → indicador verde → Crear cuenta → Firebase Authentication → confirmación de verificación por Admin SDK → configuración de contraseña maestra. La huella se activa después desde ajustes; verificar el correo no activa biometría.

## Configuración pendiente del servicio

No se ha enviado ningún correo real ni actualizado el Apps Script remoto. El repositorio no contiene acceso administrativo al servicio anterior. Esta implementación requiere configurar el backend y actualizar el Apps Script antes de funcionar en el teléfono.

Se incorporó la plantilla HTML original facilitada por el usuario en `mail-apps-script.gs`: mismo logo, colores, tarjeta y adaptación móvil. La caducidad indicada ahora es de diez minutos y coincide con la validación del servidor. El remitente es la cuenta que autoriza el despliegue de Apps Script para ejecutarse como ella; no se cambia escribiendo una dirección en Android.

El segundo script facilitado, de recuperación, solo enviaba un código recibido como parámetro: no comprobaba su caducidad ni restablecía Firebase. Actualmente `ForgotPasswordActivity` solicita el enlace oficial mediante `sendPasswordResetEmail`; no utiliza ese script ni restablece la contraseña maestra. No se ha implementado recuperación de cuenta por código numérico con la plantilla anterior.

**Actualizar Apps Script no elimina por sí solo «Servicio no configurado».** También debe ejecutarse el backend de `server/index.js`, configurarse sus variables y compilarse Android con `vaultApiUrl` apuntando a ese backend. La URL `/exec` de Apps Script corresponde a `MAIL_SCRIPT_URL`, no a `vaultApiUrl`.

1. Conservar la cuenta remitente anterior abriendo su proyecto Apps Script. Revisar e incorporar `mail-apps-script.gs` como reemplazo del endpoint de envío. Retirar el antiguo `doGet` y sus despliegues accesibles: la clave antigua incluida en la APK debe considerarse expuesta.
2. Generar dos secretos aleatorios independientes de al menos 32 bytes fuera del código fuente. Configurar `MAIL_SCRIPT_SECRET` en las propiedades del script y en el entorno del backend. Configurar el segundo como `EMAIL_VERIFICATION_SECRET` solo en el backend. No enviarlos por chat ni incluirlos en Gradle/APK.
3. Publicar una versión del Apps Script como aplicación web que ejecute con la cuenta remitente y sea accesible por el backend. El secreto en POST autentica el envío. Configurar su URL `/exec` como `MAIL_SCRIPT_URL` en el backend. Se usa MailApp; se aplican las cuotas gratuitas de Google. No se ha contratado otro proveedor.
4. Configurar `vaultApiUrl` con el endpoint HTTPS `/vault` del mismo servidor. La APK utiliza `/registration/request`, `/registration/verify` y `/registration/complete` en ese origen. No exponga el backend por HTTP público.
5. Las colecciones `registrationChallenges` y `registrationRateLimits` son exclusivas del servidor; las reglas por defecto del repositorio niegan su acceso a clientes. Configurar TTL por `expiresAt` si se desea limpieza automática. El límite diario se conserva durante 24 horas, independientemente de la caducidad de 10 minutos del código.
6. Configurar límites de solicitudes en el proxy. El servidor limita a cinco envíos diarios por correo y veinte por dirección del socket. Detrás de un proxy todos los clientes pueden compartir esa dirección: es una limitación conservadora deliberada. No se confía ciegamente en `X-Forwarded-For`; adaptar la identificación del cliente solo al conocer y restringir el proxy de confianza. Añadir protección contra abuso antes de exposición pública.

## Controles

- Antes de enviar se consulta `Firebase Admin.getUserByEmail`. Un correo registrado devuelve `EMAIL_ALREADY_REGISTERED` y no recibe código de alta. Solo el propietario autenticado de una cuenta todavía no verificada puede reanudar su registro. Los fallos de consulta no se interpretan como correo disponible.
- Se distinguen servicio sin configurar, correo registrado, límite de solicitudes, fallo de consulta y fallo de envío. La APK sin `vaultApiUrl` informa que no está configurada y no puede comprobar cuentas remotamente.
- Mostrar explícitamente que un correo está registrado permite conocer su existencia, según la experiencia solicitada. La consulta queda detrás del límite de solicitudes; mantener también la protección de abuso del proxy.

- CSPRNG para código y capacidad de sesión; código de un solo flujo, diez minutos, cinco intentos, reenvío tras un minuto. El reenvío invalida el desafío anterior.
- Código guardado como HMAC con secreto del servidor, vinculado a correo y capacidad. No hay código ni secreto de correo en preferencias o logs de Android.
- La APK conserva temporalmente la capacidad en memoria. Al cerrar el proceso o cambiar el correo se solicita verificación nuevamente; un booleano visual no basta para finalizar el registro.
- Solo `/registration/complete`, con token Firebase válido y desafío verificado del mismo correo, establece `emailVerified=true` mediante Admin SDK. La creación de bóveda nueva exige el claim `email_verified` del token actualizado.
- Crear cuenta puede dejar una cuenta Firebase sin finalizar si falla la conexión después de su creación. La misma sesión puede reintentar tras verificar el correo otra vez. Si se pierde la sesión, iniciar sesión en la cuenta existente; no se eliminan cuentas automáticamente.
- Los endpoints no reciben contraseñas de cuenta ni maestras. No constituyen recuperación de bóveda.

## Validación

`node --test server/mail-apps-script.test.js server/email-verification.test.js server/protocol.test.js` verifica el protocolo y el adaptador de correo sin enviar mensajes ni usar datos reales: 18 pruebas aprobadas. Falta la prueba integral con Firebase Emulator y con la cuenta remitente configurada, además de la prueba visual en un teléfono. No confundir una compilación correcta con un correo efectivamente entregado.

Referencias oficiales: [Firebase Admin: gestión de usuarios](https://firebase.google.com/docs/auth/admin/manage-users), [Apps Script MailApp](https://developers.google.com/apps-script/reference/mail/mail-app), [Apps Script Web Apps](https://developers.google.com/apps-script/guides/web).
