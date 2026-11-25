import os
import glob

layout_dir = r'C:/Users/juanj/Documents/ProyectoAndroid/IronVault/app/src/main/res/layout'

replacements = [
    # Settings
    ('android:text="Editar perfil"', 'android:text="@string/settings_edit_profile"'),
    ('android:text="Configuración"', 'android:text="@string/label_configuration"'),
    ('android:text="Seguridad y bóveda"', 'android:text="@string/settings_security_title"'),
    ('android:text="Bloqueo, accesos y clave maestra"', 'android:text="@string/settings_security_sub"'),
    ('android:text="Categorías"', 'android:text="@string/label_categories"'),
    ('android:text="Organiza tus categorías guardadas"', 'android:text="@string/settings_categories_sub"'),
    ('android:text="Idioma"', 'android:text="@string/label_language"'),
    ('android:text="Español"', 'android:text="@string/settings_language_default"'),
    ('android:text="Cerrar sesión"', 'android:text="@string/settings_logout"'),
    ('android:text="IronVault • versión 2.4.1"', 'android:text="@string/settings_version"'),
    # Security
    ('android:text="Seguridad"', 'android:text="@string/security_title"'),
    ('android:text="Tu bóveda está protegida"', 'android:text="@string/security_vault_protected"'),
    ('android:text="Bloquear bóveda ahora"', 'android:text="@string/security_btn_lock_now"'),
    ('android:text="Cambiar clave maestra"', 'android:text="@string/security_change_key"'),
    ('android:text="Bloqueo automático"', 'android:text="@string/security_auto_lock"'),
    ('android:text="Tras el tiempo de inactividad"', 'android:text="@string/security_auto_lock_sub"'),
    ('android:text="5 minutos"', 'android:text="@string/security_default_auto_lock"'),
    ('android:text="Intentos de acceso"', 'android:text="@string/security_attempts"'),
    ('android:text="3 intentos antes de bloquear"', 'android:text="@string/security_attempts_sub"'),
    ('android:text="3 Intentos"', 'android:text="@string/security_default_attempts"'),
    ('android:text="Historial de accesos"', 'android:text="@string/security_history"'),
    ('android:text="Ver actividad reciente"', 'android:text="@string/security_history_sub"'),
    ('android:text="Última vez desbloqueada: hoy, 7:32 p. m."', 'android:text="@string/security_last_unlock"'),
    # Login
    ('android:text="Iniciar sesión"', 'android:text="@string/login_title"'),
    ('android:text="Ingresa tu correo y tu contraseña."', 'android:text="@string/login_subtitle"'),
    ('android:hint="Correo electrónico"', 'android:hint="@string/login_hint_email"'),
    ('android:hint="Contraseña de la cuenta"', 'android:hint="@string/login_hint_password"'),
    ('android:text="Recordarme"', 'android:text="@string/login_remember"'),
    ('android:text="¿Olvidaste tu contraseña?"', 'android:text="@string/login_forgot"'),
    ('android:text="¿No tienes cuenta? "', 'android:text="@string/login_no_account"'),
    ('android:text="Regístrate"', 'android:text="@string/login_register"'),
    # Welcome
    ('android:text="Crear una cuenta"', 'android:text="@string/welcome_btn_register"'),
    ('android:text="Iniciar Sesion"', 'android:text="@string/welcome_btn_login"'),
    ('android:text="Continuar como invitado"', 'android:text="@string/welcome_guest_mode"'),
    ('android:text="Los datos se guardan solo en este dispositivo"', 'android:text="@string/welcome_guest_note"'),
    ('android:text="Acerca de"', 'android:text="@string/welcome_about"'),
    ('android:text="Cifrado de extremo a extremo"', 'android:text="@string/welcome_badge"'),
    ('android:text="Al continuar aceptas nuestros términos\ny política de privacidad."', 'android:text="@string/welcome_terms"'),
    ('android:text="¿Necesitas ayuda? v1.0.0"', 'android:text="@string/welcome_help"'),
    # Splash
    ('android:text="Desbloqueando tu bóveda..."', 'android:text="@string/splash_unlocking"'),
    ('android:text="IronVault v1.0.0"', 'android:text="@string/splash_version"'),
    ('android:text="SECURE PASSWORD\nMANAGER"', 'android:text="@string/welcome_subtitle"'),
    # Unlock vault
    ('android:text="Ingresa tu contraseña maestra"', 'android:text="@string/unlock_title"'),
    ('android:text="Ya iniciaste sesión. Ahora ingresa la llave que protege tu bóveda."', 'android:text="@string/unlock_subtitle_default"'),
    ('android:text="Si la olvidas, NO se podrá recuperar bajo ningún motivo."', 'android:text="@string/unlock_warning"'),
    ('android:text="Contraseña maestra"', 'android:text="@string/unlock_label_password"'),
    ('android:text="Iniciar la bóveda"', 'android:text="@string/unlock_btn"'),
    ('android:text="Si la olvidas, no podremos recuperarla."', 'android:text="@string/unlock_footer"'),
    ('android:text="Bóveda bloqueada temporalmente"', 'android:text="@string/unlock_lockout_title"'),
    ('android:text="Demasiados intentos fallidos"', 'android:text="@string/unlock_lockout_subtitle"'),
    # Master password
    ('android:text="Crea tu contraseña maestra"', 'android:text="@string/master_title"'),
    ('android:text="Ya iniciaste sesión. Ahora crea la llave que protegerá tu bóveda."', 'android:text="@string/master_subtitle"'),
    ('android:text="Mínimo 6 caracteres"', 'android:text="@string/master_min_chars"'),
    ('android:text="Crear bóveda"', 'android:text="@string/master_btn"'),
    # Change master key
    ('android:text="Cambiar clave maestra"', 'android:text="@string/change_key_title"'),
    ('android:text="Guardar nueva clave"', 'android:text="@string/change_key_btn"'),
    ('android:hint="Contraseña actual"', 'android:hint="@string/change_key_hint_current"'),
    ('android:hint="Nueva clave maestra"', 'android:hint="@string/change_key_hint_new"'),
    ('android:hint="Confirmacion nueva clave maestra"', 'android:hint="@string/change_key_hint_confirm"'),
    # Main
    ('android:text="Tu bóveda"', 'android:text="@string/main_vault_title"'),
    ('android:hint="Buscar contraseña"', 'android:hint="@string/main_search_hint"'),
    ('android:text="ESTADO GENERAL"', 'android:text="@string/main_general_state"'),
    ('android:text="Tus contraseñas"', 'android:text="@string/main_your_passwords"'),
    ('android:text="◀ Ant"', 'android:text="@string/main_prev"'),
    ('android:text="Sig ▶"', 'android:text="@string/main_next"'),
    ('android:text="Débiles"', 'android:text="@string/label_weak"'),
    # Generator
    ('android:text="Generador de contraseñas"', 'android:text="@string/generator_title"'),
    ('android:text="📋  Copiar al Portapapeles"', 'android:text="@string/generator_copy"'),
    ('android:text="Longitud"', 'android:text="@string/generator_length"'),
    ('android:text="Mayúsculas (A-Z)"', 'android:text="@string/generator_uppercase"'),
    ('android:text="Minúsculas (a-z)"', 'android:text="@string/generator_lowercase"'),
    ('android:text="Números (0-9)"', 'android:text="@string/generator_numbers"'),
    ('android:text="Símbolos (!_$%&amp;)"', 'android:text="@string/generator_symbols"'),
    ('android:text="Generar de nuevo"', 'android:text="@string/generator_regenerate"'),
    ('android:text="Continuar con esta contraseña"', 'android:text="@string/generator_accept"'),
    ('android:text="Fortaleza: Excelente (128 bits de entropía)"', 'android:text="@string/generator_strength_hint"'),
    # New credential
    ('android:text="Nueva contraseña"', 'android:text="@string/new_cred_title"'),
    ('android:text="Guarda un nuevo acceso en tu bóveda"', 'android:text="@string/new_cred_subtitle"'),
    ('android:hint="Servicio o plataforma"', 'android:hint="@string/new_cred_hint_service"'),
    ('android:hint="Usuario o correo"', 'android:hint="@string/new_cred_hint_user"'),
    ('android:hint="Contraseña"', 'android:hint="@string/new_cred_hint_password"'),
    ('android:hint="URL del servicio (opcional)"', 'android:hint="@string/new_cred_hint_url"'),
    ('android:hint="Categoría"', 'android:hint="@string/new_cred_hint_category"'),
    ('android:text="Seleccionar categoría"', 'android:text="@string/new_cred_select_category"'),
    ('android:hint="Notas adicionales (opcional)"', 'android:hint="@string/new_cred_hint_notes"'),
    ('android:text="Guardar credencial"', 'android:text="@string/new_cred_btn"'),
    ('android:text="Tu contraseña se guarda cifrada en la bóveda."', 'android:text="@string/new_cred_encrypted_note"'),
    # Profile
    ('android:text="Mi perfil"', 'android:text="@string/profile_title"'),
    ('android:text="Sin nombre"', 'android:text="@string/profile_no_name"'),
    ('android:text="Editar nombre"', 'android:text="@string/profile_edit_name"'),
    ('android:text="Puntaje de seguridad"', 'android:text="@string/profile_security_score"'),
    ('android:text="Sin contraseñas aún"', 'android:text="@string/profile_no_passwords"'),
    ('android:text="Avisos de seguridad"', 'android:text="@string/profile_security_warnings"'),
    ('android:text="Contraseñas por categoría"', 'android:text="@string/profile_passwords_by_category"'),
    ('android:text="Tu cuenta"', 'android:text="@string/profile_account"'),
    ('android:text="Tipo de cuenta"', 'android:text="@string/profile_account_type"'),
    ('android:text="Correo verificado"', 'android:text="@string/profile_email_verified"'),
    ('android:text="Último inicio de sesión"', 'android:text="@string/profile_last_login"'),
    ('android:text="Miembro desde"', 'android:text="@string/profile_member_since"'),
    ('android:text="Cambiar contraseña"', 'android:text="@string/profile_change_password"'),
    # History
    ('android:text="Borrar historial"', 'android:text="@string/history_clear"'),
    ('android:text="Desbloqueo exitoso"', 'android:text="@string/history_unlock_success"'),
    ('android:text="Contraseña incorrecta"', 'android:text="@string/history_wrong_password"'),
    ('android:text="Registro de intentos de desbloqueo\nde tu bóveda en este equipo."', 'android:text="@string/history_subtitle"'),
    # Vault security
    ('android:text="Seguridad y bóveda"', 'android:text="@string/vault_security_title"'),
    ('android:text="Contraseña principal"', 'android:text="@string/vault_security_main_password"'),
    ('android:text="Cambiar la clave principal de acceso"', 'android:text="@string/vault_security_main_password_sub"'),
    ('android:text="Desbloqueo biométrico"', 'android:text="@string/vault_security_biometric"'),
    ('android:text="Usar huella digital o reconocimiento"', 'android:text="@string/vault_security_biometric_sub"'),
    ('android:text="Exportar datos de la bóveda"', 'android:text="@string/vault_security_export"'),
    ('android:text="Crear copia de seguridad cifrada"', 'android:text="@string/vault_security_export_sub"'),
    ('android:text="Borrado de datos"', 'android:text="@string/vault_security_delete"'),
    ('android:text="Eliminar registros"', 'android:text="@string/vault_security_delete_sub"'),
    ('android:text="IronVault • seguridad cifrada AES-256"', 'android:text="@string/vault_security_footer"'),
    # Business rules
    ('android:text="Reglas de Negocio"', 'android:text="@string/business_title"'),
    ('android:text="Regla de Negocio Principal"', 'android:text="@string/business_rule_title"'),
    ('android:text="Funcionalidades de la App"', 'android:text="@string/business_features_title"'),
    ('android:text="Gestión segura de credenciales y notas."', 'android:text="@string/business_feature_1"'),
    ('android:text="Autenticación biométrica integrada."', 'android:text="@string/business_feature_2"'),
    ('android:text="Bloqueo automático por inactividad."', 'android:text="@string/business_feature_3"'),
    ('android:text="Exportación de datos de bóveda cifrada."', 'android:text="@string/business_feature_4"'),
    ('android:text="Políticas de Seguridad"', 'android:text="@string/business_policy_title"'),
    ('android:text="IronVault • Módulo de Reglas y Lógica de Negocio"', 'android:text="@string/business_footer"'),
    # Guest mode
    ('android:text="Modo invitado"', 'android:text="@string/guest_title"'),
    ('android:text="Entiendo, continuar sin cuenta"', 'android:text="@string/guest_btn_continue"'),
    ('android:text="Mejor crear una cuenta"', 'android:text="@string/guest_btn_create"'),
    # Categories
    ('android:text="Nueva Categoría"', 'android:text="@string/category_new_title"'),
    ('android:text="Editar Categoría"', 'android:text="@string/category_edit_title"'),
    ('android:text="Guardar Categoría"', 'android:text="@string/category_btn_save"'),
    ('android:text="Grupo / Clasificación"', 'android:text="@string/category_label_group"'),
    ('android:text="Trabajo y Educación"', 'android:text="@string/category_group_work"'),
    # Auto lock dialog
    ('android:text="15 segundos"', 'android:text="@string/lock_15s"'),
    ('android:text="30 segundos"', 'android:text="@string/lock_30s"'),
    ('android:text="1 minuto"', 'android:text="@string/lock_1m"'),
    ('android:text="3 minutos"', 'android:text="@string/lock_3m"'),
    ('android:text="10 minutos"', 'android:text="@string/lock_10m"'),
    ('android:text="Desactivar"', 'android:text="@string/lock_disable"'),
    # Attempts dialog
    ('android:text="5 Intentos"', 'android:text="@string/attempts_5"'),
    ('android:text="7 Intentos"', 'android:text="@string/attempts_7"'),
    # Logout dialog
    ('android:text="Cerrar sesión"', 'android:text="@string/settings_logout"'),
    ('android:text="¿Estás seguro de que quieres cerrar sesión?"', 'android:text="@string/logout_message"'),
    # Biometric
    ('android:text="Autenticación biométrica"', 'android:text="@string/biometric_title"'),
    ('android:text="Este diálogo se cerrará en 5 segundos"', 'android:text="@string/biometric_countdown"'),
    ('android:text="Usar contraseña maestra"', 'android:text="@string/biometric_use_password"'),
    # Edit name dialog
    ('android:text="Editar nombre"', 'android:text="@string/edit_name_title"'),
    ('android:hint="Tu nombre completo"', 'android:hint="@string/edit_name_hint"'),
    # Delete category dialog
    ('android:text="Eliminar categoría"', 'android:text="@string/delete_category_title"'),
    # Export dialog
    ('android:text="¿Descargar tus datos?"', 'android:text="@string/export_title"'),
    # Guest account dialog
    ('android:text="Crear una cuenta"', 'android:text="@string/guest_account_create"'),
    ('android:text="Ya tengo una cuenta"', 'android:text="@string/guest_account_login"'),
    ('android:text="Seguir como invitado"', 'android:text="@string/guest_account_continue"'),
    # Verify dialog
    ('android:text="Verifica tu correo"', 'android:text="@string/dialog_verify_title"'),
    # Register
    ('android:text="Crea tu cuenta"', 'android:text="@string/register_title"'),
    ('android:text="Solo toma un minuto."', 'android:text="@string/register_subtitle"'),
    ('android:text="Acepto los "', 'android:text="@string/register_terms_prefix"'),
    ('android:text="Términos"', 'android:text="@string/register_terms"'),
    ('android:text=" y la "', 'android:text="@string/register_terms_and"'),
    ('android:text="Política de Privacidad"', 'android:text="@string/register_privacy"'),
    ('android:text="Crear cuenta"', 'android:text="@string/register_btn"'),
    # Forgot password
    ('android:text="Recuperar contraseña"', 'android:text="@string/forgot_title"'),
    ('android:text="Inicia sesión"', 'android:text="@string/forgot_login"'),
    ('android:text="¿Recordaste tu contraseña? "', 'android:text="@string/forgot_remember"'),
    # Verify code
    ('android:text="Confirmar código"', 'android:text="@string/verify_title"'),
    ('android:text="Código enviado a tu correo"', 'android:text="@string/verify_subtitle"'),
    ('android:text="Verificar código"', 'android:text="@string/verify_btn"'),
    # Reset password
    ('android:text="Restablecer contraseña"', 'android:text="@string/reset_title"'),
    ('android:text="Guardar contraseña"', 'android:text="@string/reset_btn"'),
    # Banner
    ('android:text="¡Cambios Guardados!"', 'android:text="@string/banner_saved_title"'),
    ('android:text="La configuración se aplicó con éxito."', 'android:text="@string/banner_saved_message"'),
    # Principal password
    ('android:text="Contraseña principal"', 'android:text="@string/principal_title"'),
    ('android:text="Cambiar contraseña principal"', 'android:text="@string/principal_btn"'),
    ('android:text="IronVault • seguridad cifrada AES-256"', 'android:text="@string/principal_footer"'),
    # Manage categories
    ('android:text="Categorías"', 'android:text="@string/manage_categories_title"'),
    ('android:hint="Buscar categoría..."', 'android:hint="@string/manage_categories_search_hint"'),
    # Category dialog
    ('android:text="Categoría"', 'android:text="@string/dialog_category_title"'),
    ('android:text="FRECUENTES"', 'android:text="@string/dialog_category_frequent"'),
    ('android:text="CUENTAS PERSONALES"', 'android:text="@string/dialog_category_personal"'),
    ('android:text="FINANZAS Y SERVICIOS"', 'android:text="@string/dialog_category_finance"'),
    ('android:text="TRABAJO Y EDUCACIÓN"', 'android:text="@string/dialog_category_work"'),
    ('android:text="Otras Claves"', 'android:text="@string/dialog_category_other"'),
    ('android:text="Nueva categoría"', 'android:text="@string/dialog_category_new"'),
    # Language dialog
    ('android:text="Idioma"', 'android:text="@string/label_language"'),
    ('android:text="Español"', 'android:text="@string/lang_spanish"'),
    ('android:text="English"', 'android:text="@string/lang_english"'),
    ('android:text="Português"', 'android:text="@string/lang_portuguese"'),
    ('android:text="Français"', 'android:text="@string/lang_french"'),
    ('android:text="Deutsch"', 'android:text="@string/lang_german"'),
    ('android:text="Italiano"', 'android:text="@string/lang_italian"'),
]

files = glob.glob(os.path.join(layout_dir, '*.xml'))
total_replacements = 0

for filepath in files:
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    for old, new in replacements:
        content = content.replace(old, new)
    
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        total_replacements += 1
        print(f'Updated: {os.path.basename(filepath)}')

print(f'\nTotal files updated: {total_replacements}')
