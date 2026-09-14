# Caboré 🛍️

Descripción: Es una plataforma digital diseñada para apoyar a emprendedores en la promoción y crecimiento de sus negocios. El sistema permitirá gestionar estrategias de marketing, publicar y organizar contenido, promocionar productos o servicios y consultar información que facilite la toma de decisiones. Su objetivo principal es brindar herramientas sencillas y accesibles que ayuden a los emprendedores a mejorar su presencia digital, atraer nuevos clientes y fortalecer sus negocios.

Objetivo general: Desarrollar un sistema de información inteligente que permite a emprendedores crear, personalizar y gestionar sus tiendas digitales, integrando herramientas de diseño, comercialización y análisis, con fin de visibilizar y facilitar la venta en un solo sistema.

Objetivos especificos: 
-Implementar funcionalidades que permitan a los usuarios crear, gestionar y vender productos en tiendas digitales de forma sencilla
-Gestionar usuarios y roles
-Permitir creación de tiendas personalizadas
-Administrar productos
-Implementar catálogo general
-Gestionar ventas
-Integrar herramientas de diseño


Proyecto desarrollado como parte del programa de formación en el **SENA**.

---

## ✨ Funcionalidades

### Para el cliente
- Explorar productos por categoría, precio, búsqueda y ofertas.
- Favoritos y carrito de compras (persistente por usuario en sesión de servidor).
- Checkout con dirección y contacto.
- Historial de pedidos (`Mis pedidos`).
- Reseñas y calificación de productos.
- Chat directo con la tienda desde su página pública.
- Inicio de sesión con correo/contraseña o con **Google**.
- Recuperación de contraseña por token.

### Para el emprendedor
- Panel con pestañas: Información, Apariencia, Contacto, Productos, Ventas, Mensajes.
- **4 plantillas visuales** para la tienda pública — Moderna, Minimalista, Elegante y Colorida — seleccionables desde el panel y aplicadas en tiempo real en `tienda.php`.
- **Estadísticas de ventas**: ventas totales, pedidos recibidos, ticket promedio, pendientes, ventas de los últimos 7 días, ranking de productos más vendidos con medallas 🥇🥈🥉 y tarjeta de **Producto Estrella**.
- **Chat** con sus clientes, con conteo de mensajes no leídos.
- Gestión de productos e inventario.

### Para el administrador
- Verificación y aprobación de productos publicados.
- Activación/suspensión de tiendas, con motivo de suspensión.
- Gestión general de usuarios y roles.

---

## 🧱 Tecnologías

| Capa | Tecnología |
|---|---|
| Backend | PHP 8.2 (mysqli + PDO) |
| Base de datos | MySQL / MariaDB |
| Frontend | HTML, CSS, JavaScript (vanilla) |
| Servidor local | XAMPP (Apache + MySQL) |

---

## 🚀 Instalación

### Requisitos
- [XAMPP](https://www.apachefriends.org/) (Apache + MySQL + PHP 8.2 o superior)
- Git

### Pasos

1. Clona el repositorio dentro de la carpeta `htdocs` de XAMPP:
   ```bash
   cd C:\xampp\htdocs
   git clone https://github.com/miguwlx/CABORE.git
   ```

2. Enciende **Apache** y **MySQL** desde el Panel de Control de XAMPP.

3. Crea la base de datos:
   - Entra a `http://localhost/phpmyadmin`.
   - Crea una base de datos llamada `cabore`.
   - Ve a la pestaña **Importar** y sube el archivo `database/cabore.sql` incluido en este repositorio.

4. Revisa `conexion.php` en la raíz del proyecto y confirma que los datos coincidan con tu entorno:
   ```php
   define('DB_HOST', 'localhost');
   define('DB_PORT', 3306); // ajusta si tu MySQL corre en otro puerto
   define('DB_USER', 'root');
   define('DB_PASS', '');
   define('DB_NAME', 'cabore');
   ```

5. Abre el proyecto en el navegador:
   ```
   http://localhost/CABORE/landing.html
   ```

### Cuentas de prueba

| Rol | Correo |
|---|---|
| Emprendedor | `emprendedor@cabore.co` |
| Cliente | `cliente@cabore.co` |
| Administrador | `dianareyec03@gmail.com` |

*(consulta con el equipo la contraseña de las cuentas de prueba)*

---

## 🗺️ Estructura de tablas principales

```
usuarios ── tiendas ── productos ── pedido_items ── pedidos
   │                        │
   │                     resenas
   │                     favoritos
   │
conversaciones ── mensajes
```

- `tiendas.plantilla`: estilo visual de la tienda pública (`moderna`, `minimalista`, `elegante`, `colorida`).
- `tiendas.activo` / `motivo_suspension`: control de administrador sobre tiendas.
- `usuarios.google_id`, `reset_token`, `reset_expira`: login con Google y recuperación de contraseña.
- `conversaciones` / `mensajes`: chat entre cliente y emprendedor.

---

## 👥 Equipo

Proyecto desarrollado por estudiantes del SENA — rama **Emprendedor** del proyecto Caboré.

## 📄 Licencia

Proyecto académico, uso educativo.
