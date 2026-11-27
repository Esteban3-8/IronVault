# Actualización de seguridad de IronVault

Estado: implementación local en validación. No se han desplegado reglas, servidor ni migraciones de usuarios reales. No usar esta revisión en producción hasta completar los pendientes de este documento.

Integración de correo vigente: Android → Google Apps Script → Firebase Authentication, sin servidor Node para la verificación. Archivos y pasos en `apps-script/verification/README.md`. `emailScriptUrl` reutiliza la URL original; todavía debe actualizarse y autorizarse el proyecto remoto. La autorización firmada de escrituras de la bóveda sigue pendiente de adaptación a la infraestructura Firebase/Apps Script del usuario.

Actualización de registro por solicitud del usuario: restaurados el menú original de código de seis dígitos, el indicador verde y la habilitación de Crear cuenta tras verificación. El código se genera y valida ahora en el servidor. El envío requiere actualizar/configurar el Apps Script de la cuenta remitente; no se ha probado una entrega real. Instrucciones y límites en `server/EMAIL_VERIFICATION.md`. La recuperación de cuenta sigue usando el enlace oficial de Firebase y no recupera la maestra.

## Auditoría del código anterior

| Gravedad | Hallazgo |
|---|---|
| Crítica | El CRUD activo enviaba las credenciales en texto plano a Firestore; Main conservaba una copia local en preferencias. EncryptionHelper no protegía ese recorrido. |
| Crítica | La pantalla de creación maestra descartaba la contraseña y guardaba un indicador. Desbloqueo y confirmaciones utilizaban la contraseña de Firebase. Recuperar la cuenta permitía acceder a los datos. |
| Alta | ChangeMasterKeyActivity guardaba master_key en texto plano y podía omitir la validación anterior. PrincipalPasswordActivity modificaba la contraseña de Firebase. |
| Alta | La biometría existente era una simulación visual sin operación criptográfica. |
| Alta | La recuperación personalizada no verificaba realmente un código ni cambiaba la contraseña. |
| Informativa | No había reglas de Firestore ni backend versionados en el repositorio. No se conocen las reglas desplegadas. |

Firebase no es la causa: el error era confundir identidad de cuenta con autorización criptográfica y almacenar contenido privado sin cifrar.

## Diseño implementado

- Firebase Authentication conserva registro, inicio/cierre de sesión y recuperación de cuenta mediante su enlace oficial.
- VaultService mantiene una sesión independiente, con DEK disponible solamente mientras la bóveda está desbloqueada. Navegación protegida y bloqueo limpian referencias y pantallas sensibles.
- Argon2id v19: memoria 64 MiB, 3 iteraciones, paralelismo 1, salida 32 bytes, salt aleatorio de 16 bytes. Perfil fijo versionado y validación antes de reservar memoria. Ejecución en dispatcher de trabajo.
- KEK derivada de la contraseña maestra; DEK aleatoria de 32 bytes. AES-256-GCM con nonce aleatorio de 12 bytes, etiqueta de 16 bytes y AAD que vincula cuenta, bóveda, propósito y revisión. Validación maestra mediante apertura autenticada de la DEK.
- Contraseña maestra nueva de 8 a 4096 (mínimo actualizado por solicitud del usuario; se recomiendan frases de 15 o más) unidades UTF-16, sin recorte ni normalización. La comparación con la contraseña de cuenta se hace localmente después de solicitar reautenticación explícita. Proveedores sin contraseña omiten esa comparación. Un cambio externo posterior de Firebase puede hacer coincidir ambas contraseñas: no puede garantizarse diferencia permanente.
- Cambio maestro reenvuelve la misma DEK, conserva el contenido cifrado y revoca la configuración biométrica local. Exige la contraseña actual.
- Biometría: Android Keystore, AndroidX BiometricPrompt, BIOMETRIC_STRONG y CryptoObject. No se acepta PIN como sustituto. La DEK se protege por dispositivo y se vincula a cuenta, bóveda y revisión de clave; la activación verifica cifrado y descifrado nativos antes de persistir. No se guarda la maestra.
- Interfaz propia reutiliza logo y colores; el diálogo protegido sigue siendo del sistema. Ninguna indicación presupone ubicación del sensor.
- Respaldo v2: contraseña independiente, Argon2id y AES-GCM. Se mantiene lectura del formato v1 PBKDF2. Importar en una bóveda existente no reemplaza su contraseña maestra.

## Autorización del servidor

Las reglas nuevas niegan escrituras directas sobre la bóveda. `server/index.js` valida el token Firebase y una firma ECDSA P-256 de la operación, vinculada a cuenta, revisión anterior y hash del sobre. Las transacciones impiden sobrescrituras concurrentes y repetición de revisiones.

La clave privada de escritura se cifra mediante la DEK. Una segunda clave privada, para cambio maestro, importación y eliminación masiva, se cifra mediante la KEK. Tener solamente Firebase o acceso biométrico no permite obtener esta segunda clave.

El servidor es necesario; sin `vaultApiUrl` la aplicación rechaza las escrituras. No se ha provisionado alojamiento. El código y las dependencias son gratuitos; esto NO garantiza que ejecutar Firebase o alojar un backend no tenga costos. No se ha contratado ningún servicio.

Preparación en un entorno de pruebas independiente:

1. Instalar Node 22 o posterior y las dependencias de `server/package.json`; generar/revisar el lockfile antes de desplegar.
2. Configurar Application Default Credentials mediante identidad del entorno y permisos mínimos. Nunca incluir cuentas de servicio en el APK o repositorio.
3. Ejecutar `node --test server/protocol.test.js`. Arrancar `server/index.js` detrás de HTTPS; escucha en 127.0.0.1:8080, configurable por PORT. Añadir límites de solicitudes y monitorización sin contenido sensible en el proxy.
4. Verificar reglas y transacciones con Firebase Emulator Suite. `firebase.json` incluye puertos de emuladores; seleccionar explícitamente un proyecto de pruebas.
5. Configurar la propiedad Gradle `vaultApiUrl` con la URL HTTPS completa del endpoint `/vault`.
6. Coordinar publicación de reglas y app: las reglas nuevas bloquean clientes antiguos. No desplegarlas sin estrategia de actualización y revisión de los usuarios afectados.

## Migración: opción A

La arquitectura anterior no permite demostrar conocimiento de una maestra criptográfica que nunca existió en el recorrido activo. Por ello no se autoriza migrar solamente mediante control de Firebase.

La aplicación muestra la clave pública de un dispositivo. Un procedimiento independiente de verificación de titularidad debe autorizar ese dispositivo mediante `users/{uid}/migration/approval`: `approved=true`, `devicePublic` con la clave mostrada, `expiresAt` Timestamp con caducidad breve y `legacyDigest` SHA-256 hexadecimal del JSON de pares `[idDocumento, updateTimeEnMilisegundos]`, ordenados igual que en el servidor. No basta un correo enviado a la cuenta recuperada. Si no existe evidencia independiente suficiente, no emitir la autorización.

El dispositivo firma la lectura heredada y el commit. El servidor comprueba que el conjunto original no cambió. La app exige una NUEVA contraseña maestra, cifra y verifica todos los registros antes de activar el sobre en una transacción. La autorización se consume al confirmar. Ante interrupción previa al commit los originales siguen disponibles para reintentar; después del commit se carga la bóveda v2. Límite actual: 200 registros heredados y tamaño del sobre permitido.

Los originales se conservan, sin acceso desde clientes bajo las reglas nuevas. Su retirada, caches locales heredadas y antiguas preferencias con secretos necesitan una limpieza supervisada después de comprobar respaldo e integridad. No se ha ejecutado ninguna limpieza destructiva. Mientras existan esos originales, un administrador con acceso a ellos puede leer los datos anteriores: la migración no deshace exposiciones previas ni ofrece conocimiento cero retroactivo.

## Límites y pendientes obligatorios

- No se han verificado las reglas realmente desplegadas ni integrado el backend con un Firebase real/emulado.
- El sobre completo se guarda como un documento: límite conservador de 900 kB serializados, contenido cifrado limitado por el protocolo. Bóvedas grandes requieren particionado con commits coherentes antes de escalar.
- Categorías personalizadas locales: diccionario cifrado íntegro con DEK, incluyendo nombres de claves. Se conservan originales locales para revisión; no se sincronizan como catálogo ni se incluyen por separado en respaldos. Las categorías asociadas a credenciales sí están cifradas en cada registro.
- Modo invitado conserva su arquitectura previa de almacenamiento local. No equivale al modelo de cuenta v2 ni a su política de contraseña maestra; necesita auditoría y migración específicas antes de anunciar las mismas garantías.
- No hay pruebas físicas de sensores laterales/traseros/bajo pantalla, cambios de inscripción, reinicio, cancelación, accesibilidad ni ciclo de vida. La aplicación es Android; no se implementó iOS.
- La interfaz de errores es conservadora y aún requiere revisión UX de estados y pruebas visuales. La estimación de fortaleza local no sustituye una evaluación de contraseñas comunes.
- Falta medir Argon2 en dispositivos físicos de gama baja y media; no se redujeron parámetros para acelerar. El perfil p=1 no es idéntico al segundo perfil recomendado por RFC 9106 (p=4).
- Las cadenas inmutables de Java/Kotlin no pueden borrarse de forma garantizada. Se limpian buffers mutables y referencias, sin prometer eliminación física total de memoria.
- Nonces aleatorios tienen probabilidad residual de colisión. El protocolo limita revisiones y rechaza repetición inmediata; no conserva un registro global de todos los nonces.
- Un administrador con control del backend puede borrar o restaurar versiones antiguas. Las firmas y AES-GCM no ofrecen disponibilidad ni detección duradera de rollback frente a un administrador que controle todo el almacenamiento. Hace falta un ancla externa para esa garantía.
- Malware, root, instrumentación del proceso, dependencias comprometidas o un dispositivo ya desbloqueado pueden exponer datos. No se afirma invulnerabilidad.
- Cambiar la maestra no protege retroactivamente copias antiguas ni rota una DEK comprometida. La rotación de DEK requiere un procedimiento adicional de recifrado.
- La eliminación masiva afecta a las credenciales activas; no elimina automáticamente respaldos, originales de migración ni otras copias. La retención/borrado completo debe definirse antes de producción.

## Archivos principales

Nuevos: `VaultCipher.kt`, `VaultEnvelope.kt`, `VaultService.kt`, `VaultSigning.kt`, `VaultBiometrics.kt`, `VaultLifecycle.kt`, `VaultMigration.kt`, `VaultAccessActivity.kt`, `VaultUi.kt`; pantalla `activity_vault_access.xml` e icono `ic_biometric_secure.xml`; `VaultCipherTest.kt`; `server/index.js`, `server/protocol.js`, `server/protocol.test.js`, `server/package.json`; `firestore.rules` y `firebase.json`.

Actualizados: actividades de registro, login, recuperación, creación/desbloqueo/cambio maestro, ajustes, seguridad, perfil, lista/detalle/edición/creación de credenciales, exportación/importación; `BaseActivity`, `IronVaultApp`, `FirebaseManager`, `CategoryStore`, servicios de respaldo y criptografía heredada; manifiesto, reglas de respaldo local, Gradle y layouts de configuración. Otras actividades protegidas reciben una guarda de acceso al crearse. No se modificó intencionalmente `.idea/markdown.xml`.

## Licencias y fuentes

Bouncy Castle bcprov-jdk18on 1.87 (MIT), AndroidX Biometric 1.1.0 y Lifecycle (Apache-2.0), kotlinx-coroutines (Apache-2.0), Firebase Admin Node 14.5.1 (Apache-2.0). Revisar también licencias transitivas y lockfile del servidor antes de distribución. No se añadieron servicios de reconocimiento facial ni procesamiento externo de contraseñas.

- https://www.rfc-editor.org/rfc/rfc9106.html
- https://developer.android.com/identity/sign-in/biometric-auth
- https://developer.android.com/jetpack/androidx/releases/biometric
- https://firebase.google.com/docs/auth/android/manage-users
- https://firebase.google.com/docs/firestore/security/get-started
- https://www.bouncycastle.org/about/license/
- https://csrc.nist.gov/pubs/sp/800/38/d/final

## Validación

Pruebas de protocolo Node ejecutadas: 6 aprobadas (identidad sin clave, vinculación de firma, separación de operaciones privilegiadas, reenvuelto, replay/rollback y parámetros maliciosos).

Validación final del 26 de noviembre de 2026: `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` terminó con BUILD SUCCESSFUL. Las 8 pruebas JVM aprobaron, sin errores ni omitidas (7,454 s): vectores RFC/NIST, roundtrip, contraseña incorrecta, manipulación, AAD, perfiles inválidos, nonces/salts, reenvuelto y respaldos. APK de depuración: `app/build/outputs/apk/debug/app-debug.apk`. Lint ya no bloquea la compilación; conserva advertencias que requieren revisión, no se añadió una línea base para ocultarlas. Se corrigió el acceso a fuentes incompatible con API 24. `git diff --check` y la comprobación sintáctica de los dos archivos JavaScript del servidor pasaron.

Pendientes: pruebas end-to-end de cuentas, reglas/emuladores, migración interrumpida, sincronización multidispositivo, benchmark móvil, pruebas físicas biométricas y revisión independiente de seguridad.

La generación del lockfile npm falló con `EBADF` al escribir archivos en este entorno. No se entrega un lockfile vacío ni se considera verificada la instalación del backend. Los tests de protocolo usan únicamente módulos estándar de Node y por ello pudieron ejecutarse sin esa instalación.
