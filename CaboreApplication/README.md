# Caboré Marketplace — Aplicación Principal Java Spring Boot

Aplicación principal del marketplace colombiano **Caboré**, migrada integralmente de PHP 8.2 a **Java 21 + Spring Boot 4.1.1 + Spring Security + Spring Data JPA (Hibernate 7.4.5) + Thymeleaf + MariaDB**.

---

## 1. Requisitos previos

- **Java JDK 21** instalado y configurado.
- **MariaDB / XAMPP MySQL** ejecutándose en el puerto **`3306`**.
- **Maven Wrapper** incluido en esta carpeta (`.\mvnw.cmd` en Windows, `./mvnw` en Linux/macOS).

---

## 2. Configuración de MariaDB (`cabore_restaurada` en puerto `3306`)

La configuración está unificada en [.env](file:///c:/xampp/htdocs/CABORE/CaboreApplication/.env), [.env.example](file:///c:/xampp/htdocs/CABORE/CaboreApplication/.env.example), [application.properties](file:///c:/xampp/htdocs/CABORE/CaboreApplication/src/main/resources/application.properties) y [application-dev.properties](file:///c:/xampp/htdocs/CABORE/CaboreApplication/src/main/resources/application-dev.properties) utilizando exactamente las variables:

- `DB_HOST=localhost`
- `DB_PORT=3306`
- `DB_NAME=cabore_restaurada`
- `DB_USERNAME=root`
- `DB_PASSWORD=`

Tanto `.env` como el valor por defecto en `application-dev.properties` apuntan a **`cabore_restaurada`**, evitando cualquier conexión accidental a otra base de datos.

Si deseas importar el esquema completo (11 tablas y 17 llaves foráneas) en un servidor nuevo, utiliza [cabore.sql](file:///c:/xampp/htdocs/CABORE/cabore.sql):
```powershell
C:\xampp\mysql\bin\mysql.exe -u root -P 3306 -e "CREATE DATABASE IF NOT EXISTS cabore_restaurada CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
cmd /c "C:\xampp\mysql\bin\mysql.exe -u root -P 3306 cabore_restaurada < ..\cabore.sql"
```

---

## 3. Variables de entorno (`.env`)

Plantilla completa en [.env.example](file:///c:/xampp/htdocs/CABORE/CaboreApplication/.env.example):

```dotenv
# Puerto HTTP de la aplicación
SERVER_PORT=8080

# --- MariaDB ---
DB_HOST=localhost
DB_PORT=3306
DB_NAME=cabore_restaurada
DB_USERNAME=root
DB_PASSWORD=

# --- Correo SMTP (Recuperación de contraseña - opcional en desarrollo) ---
# MAIL_HOST=smtp.gmail.com
# MAIL_PORT=587
# MAIL_USERNAME=tu_correo@gmail.com
# MAIL_PASSWORD=tu_clave_de_aplicacion
# MAIL_FROM=no-reply@cabore.co
# APP_BASE_URL=http://localhost:8080

# --- Google Sign-In (OAuth 2.0 - opcional) ---
# GOOGLE_CLIENT_ID=tu_client_id.apps.googleusercontent.com

# --- Clave de instalación para /crear-admin (opcional) ---
# ADMIN_SETUP_KEY=define_una_clave_segura_propia
```

### Servicios externos opcionales
1. **Correo SMTP (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`)**:
   - Cuando `MAIL_USERNAME` y `MAIL_PASSWORD` están configurados, `RecuperacionPasswordService` envía el correo HTML real con el enlace `/restablecer-password?token=...`.
   - Si no están configurados, el token criptográfico de 64 caracteres y su expiración (`+1 hora`) se guardan igualmente en `usuarios.reset_token` y `usuarios.reset_expira` sin producir errores.
2. **Google Sign-In (`GOOGLE_CLIENT_ID`)**:
   - Cuando `GOOGLE_CLIENT_ID` está configurado con un Client ID válido de Google Cloud Console (origen autorizado `http://localhost:8080`), `HttpGoogleTokenVerifier` valida el `id_token` contra `https://oauth2.googleapis.com/tokeninfo`.
3. **Creación controlada de administrador (`ADMIN_SETUP_KEY`)**:
   - El endpoint `/crear-admin` (migración de `crear_admin.php`) solo permite registrar un administrador si `ADMIN_SETUP_KEY` está definido y coincide con la clave ingresada en el formulario.

---

## 4. Ejecución y Pruebas

### Ejecutar toda la suite de pruebas (incluyendo MariaDB real)
```powershell
cd C:\xampp\htdocs\CABORE\CaboreApplication
.\mvnw.cmd -B clean test
```

### Iniciar la aplicación Spring Boot
```powershell
cd C:\xampp\htdocs\CABORE\CaboreApplication
.\mvnw.cmd spring-boot:run
```
Disponible en: **`http://localhost:8080`**

---

## 5. Relación con los archivos históricos del repositorio

- **Archivos PHP en la raíz (`../*.php`)**: se conservan únicamente como referencia histórica y respaldo de la versión original en PHP 8.2.
- **Carpeta `../cabore-laravel/`**: contiene únicamente el esqueleto inicial por defecto de Laravel 12 sin controladores, modelos ni vistas de Caboré. Está inactiva y no interfiere con Spring Boot.
