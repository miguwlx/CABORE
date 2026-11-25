# Caboré 🛍️ — Marketplace Colombiano (Java Spring Boot + MariaDB)

**Descripción:** Plataforma digital diseñada para apoyar a emprendedores colombianos en la promoción, comercialización y crecimiento de sus negocios. Permite crear y personalizar tiendas digitales, gestionar inventarios y pedidos, moderar catálogos desde un panel administrativo, interactuar mediante chat directo entre clientes y tiendas y emitir facturas y reportes de ventas.

**Objetivo general:** Desarrollar un sistema de información que permita a emprendedores crear, personalizar y gestionar sus tiendas digitales, integrando herramientas de diseño, comercialización y análisis para visibilizar y facilitar las ventas en una sola plataforma.

**Objetivos específicos:**
- Implementar funcionalidades que permitan a los usuarios crear, gestionar y vender productos en tiendas digitales de forma sencilla.
- Gestionar usuarios, seguridad y permisos por roles (`cliente`, `emprendedor`, `administrador`).
- Permitir la creación y personalización de tiendas con plantillas visuales, logo, banner y colores de marca.
- Administrar productos, ofertas, destacados, inventario y moderación previa a publicación.
- Implementar el catálogo general con filtros avanzados, carrito persistente, favoritos, reseñas verificadas y facturación.
- Integrar mensajería directa (chat) entre clientes y emprendedores.

Proyecto desarrollado como parte del programa de formación en el **SENA**.

---

## 🏗️ Arquitectura y Estructura del Repositorio

> **Aplicación principal:** **`CaboreApplication/` (Java 21 + Spring Boot 4.1.1)** es la aplicación principal y oficial en producción y desarrollo.

| Carpeta / Archivos | Propósito y estado actual |
| :--- | :--- |
| **`CaboreApplication/`** | **Aplicación principal en Java Spring Boot 4.1.1** (Spring MVC, Spring Security, Spring Data JPA / Hibernate 7.4.5, Thymeleaf, Spring Mail, MariaDB). Contiene el 100 % de los módulos migrados y la suite de pruebas automatizadas. |
| **`cabore.sql`** | Script SQL maestro con el esquema completo de MariaDB (11 tablas y 17 llaves foráneas, incluyendo `carrito_items`, `conversaciones` y `mensajes`). |
| **`*.php` / `*.html` (raíz)** | **Referencia histórica del sistema original en PHP 8.2.** Se conservan intactos como respaldo y referencia de reglas de negocio y diseño visual. |
| **`cabore-laravel/`** | **Esqueleto base de Laravel 12 sin código de negocio de Caboré** (solo contiene la plantilla por defecto `welcome.blade.php` y migraciones estándar de Laravel). Se conserva como carpeta histórica inactiva y **no interfiere** con `CaboreApplication`. |

---

## 🧱 Tecnologías (Aplicación Principal Spring Boot)

| Capa | Tecnología |
| :--- | :--- |
| Lenguaje y Runtime | Java 21 |
| Backend Framework | Spring Boot 4.1.1 (Spring Web MVC, Spring Security, Validation, Mail) |
| Persistencia / ORM | Spring Data JPA + Hibernate ORM 7.4.5 |
| Motor de Plantillas | Thymeleaf + HTML5, CSS3, JavaScript |
| Base de Datos | MariaDB / MySQL (puerto `3306`, esquema `cabore_restaurada`) |
| Pruebas Automatizadas | JUnit 5, Spring Boot Test, MockMvc, Spring Security Test, H2 (perfil `test`) y MariaDB real (perfil `dev`) |

---

## ✨ Módulos y Funcionalidades Implementadas

### 1. Módulo Cliente (`/cliente/**`)
- **Catálogo general (`/cliente`)**: búsqueda por texto, filtrado por categoría, rango de precio, ofertas y disponibilidad, ordenamiento y sección de productos destacados.
- **Ficha de producto (`/cliente/producto/{id}`)**: galería, stock en tiempo real, botón de contacto por WhatsApp, inicio directo de chat con la tienda y sistema de **reseñas y calificaciones verificadas** (solo para clientes con pedido en estado `entregado`).
- **Carrito persistente en base de datos (`/cliente/carrito`, `/cliente/carrito/ajax`)**: sincronizado con la tabla `carrito_items`, drawer lateral interactivo y ajuste automático ante cambios de stock.
- **Checkout e inventario (`/cliente/checkout`)**: confirmación de compra con reserva y descuento transaccional de stock en `productos`, guardado opcional de dirección/teléfono en el perfil.
- **Pedidos y facturas (`/cliente/pedidos`, `/cliente/pedido/{id}`, `/cliente/factura/{id}`)**: línea de tiempo del pedido, cancelación de pedidos pendientes con devolución automática de stock, y factura imprimible con soporte para pedidos históricos (`precio_unitario` como respaldo cuando `precio_unit` es `0.00`).
- **Favoritos (`/cliente/favoritos`)** y **Perfil (`/cliente/perfil`)**: gestión de datos personales, contador de pedidos realizados y cambio/creación de contraseña.
- **Insignia de mensajes no leídos**: notificación en tiempo real en la barra superior del cliente.

### 2. Módulo Emprendedor (`/emprendedor/**`) y Tienda Pública (`/tienda/{id}`)
- **Dashboard e inventario (`/emprendedor`)**: métricas de catálogo, productos activos, destacados y valor total del inventario (`precio × stock`).
- **Ventas y analítica**: ventas totales históricas de la tienda, pedidos recibidos, ticket promedio, pendientes, tarjeta de **👑 Producto Estrella** (unidades, ingresos y porcentaje sobre ventas), gráfico de los últimos 7 días y ranking con medallas 🥇🥈🥉.
- **Gestión de productos (`/emprendedor/productos/**`)**: creación, edición con subida validada de imágenes (JPEG, PNG, WEBP, GIF hasta 5 MB verificadas por firma binaria), reenvío automático a moderación al corregir un producto rechazado y archivado lógico cuando el producto tiene ventas históricas.
- **Gestión de pedidos, reportes y facturas**: actualización de estado por AJAX (`/emprendedor/pedidos/estado/ajax`), reportes por rango de fechas con gráfico de 14 días, exportación CSV con BOM UTF-8 (`/emprendedor/reportes/csv`) y facturas filtradas exclusivamente con los ítems de la tienda (`/emprendedor/factura/{id}`).
- **Personalización y Tienda Pública (`/tienda/{id}`)**: configuración de información, contacto (WhatsApp e Instagram) y apariencia con las **4 plantillas originales** (`moderna`, `minimalista`, `elegante`, `colorida`), color principal (`--brand`), logo y banner, y barra de navegación contextual según el visitante.

### 3. Módulo Administrador (`/admin/**`)
- **Resumen general**: métricas globales de usuarios, tiendas activas, productos pendientes de verificación y ventas totales.
- **Verificación de productos (`/admin/productos/verificar/ajax`)**: aprobación o rechazo con motivo obligatorio; solo los productos aprobados de tiendas activas aparecen en el catálogo público.
- **Reportes y facturas (`/admin/reportes/csv`, `/admin/factura/{id}`)**: filtrado por fechas, estado y tienda, gráfico de 14 días, top tiendas, top productos y exportación CSV.
- **Gestión de usuarios, tiendas y categorías**: suspensión/reactivación de cuentas (con bloqueo de login para suspendidos y protección entre administradores), suspensión de tiendas con motivo obligatorio y sincronización `activo`/`activa`, y CRUD de categorías con protección contra borrado si tienen productos asignados.

### 4. Chat, Recuperación de Contraseña, Google Auth y Creación Controlada de Admin
- **Chat entre clientes y tiendas (`/chat`, `/chat/iniciar`, `/chat/enviar`)**: conversación única por par `(tienda_id, cliente_id)`, historial de mensajes, marcado automático de mensajes leídos y protección `403 Forbidden` frente a conversaciones ajenas.
- **Recuperación de contraseña (`/olvide-password`, `/restablecer-password`)**: generación de token criptográfico de 64 caracteres hexadecimales con expiración de 1 hora, envío por SMTP mediante Spring Mail e invalidación inmediata tras el cambio de contraseña.
- **Autenticación con Google (`POST /auth/google`)**: verificación de `id_token` contra Google OAuth2, vinculación automática por `google_id` o `correo`, registro de nuevos clientes/emprendedores (con creación automática de tienda) y bloqueo de cuentas suspendidas.
- **Instalación controlada de administrador (`/crear-admin`)**: migración de `crear_admin.php` protegida por la variable de entorno `ADMIN_SETUP_KEY`.

---

## ⚙️ Configuración y Variables de Entorno

La configuración de referencia se encuentra unificada en:
- [CaboreApplication/.env](file:///c:/xampp/htdocs/CABORE/CaboreApplication/.env)
- [CaboreApplication/.env.example](file:///c:/xampp/htdocs/CABORE/CaboreApplication/.env.example)
- [application.properties](file:///c:/xampp/htdocs/CABORE/CaboreApplication/src/main/resources/application.properties)
- [application-dev.properties](file:///c:/xampp/htdocs/CABORE/CaboreApplication/src/main/resources/application-dev.properties)

### Variables de entorno (`CaboreApplication/.env`)

```dotenv
# Puerto HTTP de la aplicación
SERVER_PORT=8080

# --- MariaDB (Configuración de referencia) ---
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

---

## 🚀 Instalación y Ejecución

### Requisitos
- **Java JDK 21**
- **XAMPP (MariaDB / MySQL)** activo en el puerto **`3306`** con la base de datos **`cabore_restaurada`**.

### 1. Verificar o importar la base de datos MariaDB
La base de datos activa es **`cabore_restaurada`** en `localhost:3306`. Si necesitas importarla en una instalación limpia:
```powershell
C:\xampp\mysql\bin\mysql.exe -u root -P 3306 -e "CREATE DATABASE IF NOT EXISTS cabore_restaurada CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
cmd /c "C:\xampp\mysql\bin\mysql.exe -u root -P 3306 cabore_restaurada < C:\xampp\htdocs\CABORE\cabore.sql"
```

### 2. Ejecutar la suite completa de pruebas automatizadas
```powershell
cd C:\xampp\htdocs\CABORE\CaboreApplication
.\mvnw.cmd -B clean test
```

### 3. Iniciar la aplicación Spring Boot
```powershell
cd C:\xampp\htdocs\CABORE\CaboreApplication
.\mvnw.cmd spring-boot:run
```
Abre en tu navegador: **`http://localhost:8080`**

---

## 🗺️ Estructura de Tablas en MariaDB (`cabore_restaurada`)

```text
usuarios ── tiendas ── productos ── pedido_items ── pedidos
   │                        │
   ├── carrito_items        ├── resenas
   │                        └── favoritos
   └── conversaciones ── mensajes
```

### Cuentas existentes en `cabore_restaurada`

| Rol | Correo |
| :--- | :--- |
| Administrador | `dianareyec03@gmail.com` |
| Emprendedor | `emprendedor@cabore.co` |
| Cliente | `cliente@cabore.co` |

---

## 👥 Equipo y Licencia

Proyecto académico desarrollado por estudiantes del **SENA** (Diana y Miguel). Uso educativo.
