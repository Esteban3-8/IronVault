# IronVault

Aplicación móvil Android para **guardar contraseñas de forma segura**, desarrollada como proyecto de la materia **Aplicaciones Móviles** de la universidad.

> **Estado actual:** fase de desarrollo de interfaces. Las pantallas se construyen primero a partir del diseño en Figma; la lógica (almacenamiento, cifrado, validaciones) se implementa en una fase posterior.

---

## Descripción

IronVault permite al usuario guardar sus credenciales (correos, redes sociales, bancos, videojuegos, educación, etc.) en una bóveda protegida por una **clave maestra** y autenticación biométrica. Las credenciales se organizan en **categorías** personalizables, cada una con su propio ícono y color.

## Equipo

Proyecto desarrollado por un equipo de tres estudiantes:

| Integrante | Rol / Módulos principales |
|---|---|
| Esteban | _(completar)_ |
| Juan José | _(completar)_ |
| Juan David | Categorías: Nueva Categoría, selector de ícono y color, Administrar Categorías |

> Completen esta tabla con los módulos que desarrolló cada integrante.

## Tecnologías

- **Lenguaje:** Kotlin
- **Interfaz:** XML (Views) con ConstraintLayout, Material Components
- **IDE:** Android Studio
- **Build:** Gradle (Kotlin DSL)
- **Control de versiones:** Git y GitHub
- **Diseño:** Figma
- **Paquete:** `com.example.myapplication`

## Pantallas

Pantallas del proyecto (carpeta `app/src/main/java/com/example/myapplication`):

**Acceso y seguridad**
- `SplashActivity`, `WelcomeActivity`
- `LoginActivity`, `RegisterActivity`, `ForgotPasswordActivity`
- `MasterPasswordActivity`, `ChangeMasterKeyActivity`, `UnlockVaultActivity`
- `GuestModeActivity`
- `SecurityActivity`, `VaultSecurityActivity`, `VerifyBottomSheet`
- `AccessHistoryActivity`, `BusinessRulesActivity`

**Bóveda y credenciales**
- `MainActivity`, `PrincipalPasswordActivity`
- `CredentialDetailActivity`, `EditCredentialActivity`, `NuevaCredencialActivity`
- `PasswordGeneratorActivity`
- `SettingsActivity`

**Categorías**
- `dialog_category.xml`: diálogo para elegir una categoría
- `AdministrarCategoriasActivity`: lista de categorías agrupadas, con contador de credenciales
- `NuevaCategoriaActivity`: crear una categoría (nombre, grupo y selección de ícono)
  - Selector de **grupo/clasificación** desplegable
  - Panel inferior con pestañas **Color** e **Ícono** (`dialog_icon_category.xml`)

### Grupos de categorías

- Frecuentes
- Cuentas personales (redes sociales, correos, videojuegos)
- Finanzas y servicios (bancos, compras)
- Trabajo y educación

## Sistema de diseño

Los colores están centralizados en `res/values/colors.xml` para mantener consistencia entre pantallas:

| Uso | Color |
|---|---|
| Fondo principal (`bg_main`) | `#14151A` |
| Tarjetas (`bg_dark_card`) | `#20222A` |
| Bordes (`border_color`) | `#35373E` |
| Naranja de marca (`orange_primary`) | `#E08238` |
| Texto principal | `#FFFFFF` |
| Texto secundario (`text_muted`) | `#6B7280` |

Los colores de categoría usan el prefijo `cat_` y los del selector de color el prefijo `pick_`. Se usa la fuente **Inter** (`inter_bold`).

## Proceso de desarrollo

### 1. Diseño
Cada pantalla se diseñó primero en **Figma**, con un tema oscuro y acento naranja.

### 2. Maquetación (fase actual)
Cada pantalla se implementa como un layout XML siguiendo el diseño, reutilizando los recursos compartidos (colores, fondos de botón `bg_button_slim`, íconos `ic_cat_*`). El código Kotlin se limita por ahora a lo mínimo para navegar (por ejemplo, el botón de volver).

### 3. Lógica (siguiente fase)
Pendiente de implementar:
- Modelo de datos de categorías y credenciales
- Persistencia local (Room o SharedPreferences)
- Cifrado de las credenciales
- Búsqueda y filtrado de categorías
- Selección y guardado de ícono y color
- Validaciones de formularios

### 4. Flujo de trabajo en equipo

El equipo trabaja directamente sobre la rama `master`, con esta rutina:

```bash
git pull origin master          # traer lo último antes de empezar
# ... trabajar ...
git add app/src                 # agregar solo el código (sin .idea)
git commit -m "Describe el cambio"
git pull --no-edit origin master
git push origin master
```

Acuerdos:
- Cada integrante trabaja en sus propias pantallas para evitar conflictos.
- Los archivos compartidos (`AndroidManifest.xml`, `colors.xml`, `strings.xml`) se editan con cuidado y se suben rápido.
- No se modifican los recursos de otros compañeros; si se necesita una variante, se crea un archivo nuevo.
- Los commits deben compilar.
- La carpeta `.idea` es configuración local y no se sube.

## Cómo ejecutar el proyecto

1. Clona el repositorio:
   ```bash
   git clone https://github.com/Esteban3-8/IronVault.git
   ```
2. Ábrelo en **Android Studio** (`File → Open` y selecciona la carpeta `IronVault`).
3. Espera a que termine la sincronización de Gradle.
4. Ejecuta la app en un emulador o en un dispositivo físico.

## Estructura del proyecto

```
IronVault/
├── app/
│   └── src/main/
│       ├── java/com/example/myapplication/   # Activities (Kotlin)
│       ├── res/
│       │   ├── layout/                       # Pantallas y diálogos (XML)
│       │   ├── drawable/                     # Íconos y fondos
│       │   └── values/                       # Colores, textos, estilos
│       └── AndroidManifest.xml
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts
```

## Próximos pasos

- [ ] Conectar las pantallas entre sí (navegación)
- [ ] Implementar la lógica de categorías
- [ ] Guardado y cifrado de credenciales
- [ ] Mover los textos fijos a `strings.xml` (soporte de idiomas)
- [ ] Pruebas en distintos tamaños de pantalla

## Licencia

Proyecto académico sin fines comerciales.
