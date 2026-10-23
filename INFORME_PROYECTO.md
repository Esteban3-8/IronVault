# Informe de Estado y Arquitectura — IronVault

> **Fecha:** Mayo 2024  
> **Proyecto:** IronVault (Aplicación Móvil Android)  
> **Objetivo:** Documento de revisión técnica y arquitectura para el equipo de desarrollo.

---

## 📌 1. Resumen General

**IronVault** es una aplicación móvil nativa para Android diseñada para la **gestión y almacenamiento seguro de contraseñas y credenciales** (correos, redes sociales, bancos, videojuegos, educación, etc.).

- **Lenguaje Principal:** Kotlin
- **Interfaz de Usuario:** Android Views (`XML`, `ConstraintLayout`, `Material Components`)
- **Configuración Gradle:**
  - `minSdk`: 24 (Android 7.0+)
  - `targetSdk`: 37
  - `compileSdk`: 37
  - Compatibilidad de Java: Java 11
- **Package Name:** `com.example.myapplication` *(pendiente por actualizar)*

---

## 🛠️ 2. Estado Actual del Proyecto

El proyecto se encuentra en la **Fase de Maquetación y Navegación Básica de UI**.
- ✅ **Layouts XML:** Diseñados y estructurados basándose en la maqueta de Figma.
- ✅ **Navegación:** Controladores Kotlin con escuchadores de eventos (`OnClickListener`), transiciones entre pantallas mediante `Intent`, e interacciones de UI (visibilidad de contraseña, diálogos bottom sheet, indicadores dinámicos de fuerza de clave).
- ⏳ **Lógica y Persistencia:** Pendiente de implementar en la siguiente fase (base de datos, cifrado, lógica de negocio y autenticación biométrica).

---

## 📱 3. Estructura de Pantallas por Módulo

El proyecto cuenta con **más de 20 actividades** organizadas modularmente:

### A. Autenticación y Acceso
- `SplashActivity.kt`: Pantalla inicial de carga de la app.
- `WelcomeActivity.kt`: Pantalla de bienvenida previa al registro o inicio de sesión.
- `LoginActivity.kt`: Formulario de inicio de sesión.
- `RegisterActivity.kt`: Formulario de registro de usuario.
- `ForgotPasswordActivity.kt`: Recuperación de cuenta/contraseña.
- `GuestModeActivity.kt`: Flujo de acceso en modo invitado.

### B. Seguridad y Clave Maestra
- `MasterPasswordActivity.kt`: Creación de la clave maestra con barras de fortaleza de clave en tiempo real.
- `UnlockVaultActivity.kt`: Pantalla para desbloquear la bóveda mediante pin o biometría.
- `ChangeMasterKeyActivity.kt`: Cambio de clave maestra existente.

### C. Dashboard y Credenciales
- `MainActivity.kt` & `PrincipalPasswordActivity.kt`: Dashboard principal con vista general de las credenciales.
- `NuevaCredencialActivity.kt`: Creación de una nueva credencial.
- `CredentialDetailActivity.kt`: Detalle de credencial (ver/copiar correo, contraseña, notas).
- `EditCredentialActivity.kt`: Formulario para editar credenciales existentes.
- `PasswordGeneratorActivity.kt`: Generador de contraseñas aleatorias configurables.

### D. Categorías
- `AdministrarCategoriasActivity.kt`: Listado de categorías agrupadas con contador de elementos.
- `NuevaCategoriaActivity.kt` & `EditarCategoriaActivity.kt`: Creación y edición con selector desplegable de grupos y bottom sheet (`dialog_icon_category.xml`) con pestañas para elegir color e ícono.

### E. Configuración e Historial
- `SecurityActivity.kt` & `VaultSecurityActivity.kt`: Opciones avanzadas de seguridad y bloqueo.
- `AccessHistoryActivity.kt`: Registro de intentos de acceso (exitosos y fallidos).
- `SettingsActivity.kt` & `VerifyBottomSheet.kt`: Ajustes generales de la app.

---

## 🎨 4. Sistema de Diseño

- **Tema Oscuro Homogéneo:** Centralizado en `res/values/colors.xml`:
  - Fondo principal: `#14151A` (`bg_main`)
  - Tarjetas y contenedores: `#20222A` (`bg_dark_card`)
  - Bordes: `#35373E` (`border_color`)
  - Acento principal: `#E08238` (`orange_primary`)
  - Texto atenuado: `#6B7280` (`text_muted`)
- **Tipografía:** Fuente *Inter* (`inter_bold`).
- **Recursos Reutilizables:** Gran colección de drawables vectoriales para categorías (`ic_cat_*`), selector de íconos (`ic_pick_*`), fondos con bordes redondeados (`bg_card_*`, `bg_button_*`) y componentes como interruptores o barras de progreso personalizadas.

---

## 🚀 5. Hoja de Ruta para la Siguiente Fase (Lógica y Persistencia)

Para que el equipo comience la implementación funcional de IronVault, se recomiendan los siguientes pasos prioritarios:

1. **Refactorización Básica (Package ID & ViewBinding):**
   - Actualizar el paquete `com.example.myapplication` a uno propio (ejemplo: `com.ironvault.app`).
   - Activar `viewBinding` en `app/build.gradle.kts` para reemplazar los `findViewById` por vinculación de vistas segura y limpia.

2. **Modelos de Datos y Persistencia:**
   - Crear las entidades de datos: `Credential`, `Category`, `AccessLog`.
   - Implementar **Room Database** con soporte para cifrado (**SQLCipher**) para guardar las contraseñas localmente de forma segura.

3. **Cifrado y Seguridad:**
   - Utilizar **AndroidX Security Crypto** (`EncryptedSharedPreferences`) para el almacenamiento de tokens/claves.
   - Usar el **Android KeyStore System** para proteger la clave maestra.
   - Integrar **AndroidX Biometric API** para habilitar el inicio de sesión por huella dactilar/rostro en `UnlockVaultActivity`.

4. **Lógica de Negocio:**
   - Conectar los formularios de `NuevaCredencialActivity` y `NuevaCategoriaActivity` con la base de datos.
   - Implementar la búsqueda y filtrado dinámico de credenciales en el dashboard principal (`MainActivity`).
   - Integrar la lógica matemática para el generador de contraseñas en `PasswordGeneratorActivity`.
