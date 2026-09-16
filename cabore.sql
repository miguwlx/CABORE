-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Servidor: localhost:3307
-- Tiempo de generación: 14-09-2026 a las 22:45:23
-- Versión del servidor: 10.4.32-MariaDB
-- Versión de PHP: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Base de datos: `cabore`
--

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `categorias`
--

CREATE TABLE `categorias` (
  `id` int(11) NOT NULL,
  `nombre` varchar(80) NOT NULL,
  `icono` varchar(10) DEFAULT '?️'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Volcado de datos para la tabla `categorias`
--

INSERT INTO `categorias` (`id`, `nombre`, `icono`) VALUES
(1, 'Ropa y Moda', '👗'),
(2, 'Accesorios', '💍'),
(3, 'Electrónica', '📱'),
(4, 'Hogar y Decoración', '🏠'),
(5, 'Alimentos y Bebidas', '🍕'),
(6, 'Belleza y Cuidado', '💄'),
(7, 'Deportes', '⚽'),
(8, 'Arte y Manualidades', '🎨'),
(9, 'Libros y Papelería', '📚'),
(10, 'Mascotas', '🐾'),
(11, 'Juguetes', '🧸'),
(12, 'Otros', '📦');

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `conversaciones`
--

CREATE TABLE `conversaciones` (
  `id` int(11) NOT NULL,
  `tienda_id` int(11) NOT NULL,
  `cliente_id` int(11) NOT NULL,
  `creado_en` datetime NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` datetime NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Volcado de datos para la tabla `conversaciones`
--

INSERT INTO `conversaciones` (`id`, `tienda_id`, `cliente_id`, `creado_en`, `actualizado_en`) VALUES
(1, 1, 2, '2026-09-14 13:36:40', '2026-09-14 14:19:53');

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `favoritos`
--

CREATE TABLE `favoritos` (
  `usuario_id` int(11) NOT NULL,
  `producto_id` int(11) NOT NULL,
  `guardado_en` datetime NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `mensajes`
--

CREATE TABLE `mensajes` (
  `id` int(11) NOT NULL,
  `conversacion_id` int(11) NOT NULL,
  `emisor_id` int(11) NOT NULL,
  `contenido` text NOT NULL,
  `leido` tinyint(1) NOT NULL DEFAULT 0,
  `creado_en` datetime NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Volcado de datos para la tabla `mensajes`
--

INSERT INTO `mensajes` (`id`, `conversacion_id`, `emisor_id`, `contenido`, `leido`, `creado_en`) VALUES
(1, 1, 2, 'Hola', 1, '2026-09-14 13:36:48'),
(2, 1, 1, 'Hola', 0, '2026-09-14 14:19:43'),
(3, 1, 1, 'Con que te puedo ayudar?', 0, '2026-09-14 14:19:53');

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `pedidos`
--

CREATE TABLE `pedidos` (
  `id` int(11) NOT NULL,
  `usuario_id` int(11) NOT NULL,
  `total` decimal(12,2) NOT NULL DEFAULT 0.00,
  `estado` enum('pendiente','confirmado','enviado','entregado','cancelado') NOT NULL DEFAULT 'pendiente',
  `direccion` text DEFAULT NULL,
  `telefono_contacto` varchar(30) DEFAULT NULL,
  `notas` text DEFAULT NULL,
  `creado_en` datetime NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Volcado de datos para la tabla `pedidos`
--

INSERT INTO `pedidos` (`id`, `usuario_id`, `total`, `estado`, `direccion`, `telefono_contacto`, `notas`, `creado_en`) VALUES
(3, 7, 254000.00, 'pendiente', 'carrera 80d #60-13 sur', '3134679909', '', '2026-07-02 13:29:29'),
(4, 7, 89000.00, 'pendiente', 'carrera 80d #60-13 sur', '3134679909', '', '2026-07-02 13:29:49');

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `pedido_items`
--

CREATE TABLE `pedido_items` (
  `id` int(11) NOT NULL,
  `pedido_id` int(11) NOT NULL,
  `producto_id` int(11) NOT NULL,
  `cantidad` int(11) NOT NULL DEFAULT 1,
  `precio_unit` decimal(12,2) NOT NULL COMMENT 'Precio al momento de compra',
  `subtotal` decimal(12,2) NOT NULL,
  `precio_unitario` decimal(10,2) NOT NULL DEFAULT 0.00
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Volcado de datos para la tabla `pedido_items`
--

INSERT INTO `pedido_items` (`id`, `pedido_id`, `producto_id`, `cantidad`, `precio_unit`, `subtotal`, `precio_unitario`) VALUES
(1, 3, 2, 1, 0.00, 0.00, 45000.00),
(2, 3, 1, 1, 0.00, 0.00, 89000.00),
(3, 3, 3, 1, 0.00, 0.00, 120000.00),
(4, 4, 1, 1, 0.00, 0.00, 89000.00);

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `productos`
--

CREATE TABLE `productos` (
  `id` int(11) NOT NULL,
  `tienda_id` int(11) NOT NULL,
  `categoria_id` int(11) DEFAULT 12 COMMENT 'Categoría del producto',
  `nombre` varchar(200) NOT NULL,
  `descripcion` text DEFAULT NULL,
  `precio` decimal(12,2) NOT NULL DEFAULT 0.00,
  `precio_oferta` decimal(12,2) DEFAULT NULL COMMENT 'Precio con descuento',
  `imagen` varchar(300) DEFAULT NULL,
  `stock` int(11) NOT NULL DEFAULT 0,
  `destacado` tinyint(1) NOT NULL DEFAULT 0 COMMENT '1 = aparece en sugerencias',
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `estado_verificacion` enum('pendiente','aprobado','rechazado') NOT NULL DEFAULT 'aprobado',
  `motivo_rechazo` varchar(255) DEFAULT NULL,
  `revisado_por` int(11) DEFAULT NULL,
  `revisado_en` datetime DEFAULT NULL,
  `creado_en` datetime NOT NULL DEFAULT current_timestamp(),
  `actualizado` datetime DEFAULT NULL ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Volcado de datos para la tabla `productos`
--

INSERT INTO `productos` (`id`, `tienda_id`, `categoria_id`, `nombre`, `descripcion`, `precio`, `precio_oferta`, `imagen`, `stock`, `destacado`, `activo`, `estado_verificacion`, `motivo_rechazo`, `revisado_por`, `revisado_en`, `creado_en`, `actualizado`) VALUES
(1, 1, 1, 'Vestido floral primavera', 'Vestido midi con estampado floral, tela liviana ideal para verano.', 89000.00, NULL, NULL, 13, 1, 1, 'aprobado', NULL, NULL, NULL, '2026-06-18 19:59:21', '2026-07-02 13:29:49'),
(2, 1, 2, 'Collar dorado minimalista', 'Collar de acero inoxidable bañado en oro de 18k, diseño elegante.', 45000.00, NULL, NULL, 29, 1, 1, 'aprobado', NULL, NULL, NULL, '2026-06-18 19:59:21', '2026-07-02 13:29:29'),
(3, 1, 6, 'Kit facial hidratante', 'Set de cremas y sérum hidratante para todo tipo de piel.', 120000.00, NULL, NULL, 9, 1, 1, 'aprobado', NULL, NULL, NULL, '2026-06-18 19:59:21', '2026-07-02 13:29:29'),
(4, 1, 1, 'Blusa lino casual', 'Blusa oversize de lino natural, cómoda y fresca.', 55000.00, NULL, NULL, 20, 0, 1, 'aprobado', NULL, NULL, NULL, '2026-06-18 19:59:21', NULL);

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `resenas`
--

CREATE TABLE `resenas` (
  `id` int(11) NOT NULL,
  `producto_id` int(11) NOT NULL,
  `usuario_id` int(11) NOT NULL,
  `calificacion` tinyint(4) NOT NULL DEFAULT 5 COMMENT 'Del 1 al 5',
  `comentario` text DEFAULT NULL,
  `creado_en` datetime NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `tiendas`
--

CREATE TABLE `tiendas` (
  `id` int(11) NOT NULL,
  `usuario_id` int(11) NOT NULL,
  `nombre` varchar(150) NOT NULL DEFAULT 'Mi tienda',
  `descripcion` text DEFAULT NULL,
  `color` varchar(9) NOT NULL DEFAULT '#c9a84c' COMMENT 'Color principal hex',
  `plantilla` varchar(20) NOT NULL DEFAULT 'moderna',
  `logo` varchar(300) DEFAULT NULL,
  `banner` varchar(300) DEFAULT NULL COMMENT 'Imagen de banner',
  `ciudad` varchar(100) DEFAULT NULL,
  `whatsapp` varchar(20) DEFAULT NULL,
  `instagram` varchar(100) DEFAULT NULL,
  `activa` tinyint(1) NOT NULL DEFAULT 1,
  `creado_en` datetime NOT NULL DEFAULT current_timestamp(),
  `actualizado` datetime DEFAULT NULL ON UPDATE current_timestamp(),
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `motivo_suspension` varchar(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Volcado de datos para la tabla `tiendas`
--

INSERT INTO `tiendas` (`id`, `usuario_id`, `nombre`, `descripcion`, `color`, `plantilla`, `logo`, `banner`, `ciudad`, `whatsapp`, `instagram`, `activa`, `creado_en`, `actualizado`, `activo`, `motivo_suspension`) VALUES
(1, 1, 'Boutique María', 'Ropa y accesorios con estilo único 🌟', '#c9a84c', 'elegante', NULL, NULL, NULL, NULL, NULL, 1, '2026-06-18 19:59:21', '2026-09-14 13:49:53', 1, NULL),
(2, 3, 'Mi tienda', 'Descripción de mi tienda', '#ec4899', 'moderna', NULL, NULL, NULL, NULL, NULL, 1, '2026-06-19 16:36:27', '2026-07-02 13:35:03', 1, NULL),
(3, 9, 'Mi tienda', 'Descripción de mi tienda', '#10b981', 'moderna', NULL, 'img/banners/banner_9_6a3adaf0d21d9.webp', NULL, NULL, NULL, 1, '2026-06-23 14:12:42', '2026-06-23 14:13:52', 1, NULL),
(4, 10, 'Mi tienda', 'Descripción de mi tienda', '#c9a84c', 'moderna', NULL, NULL, NULL, NULL, NULL, 1, '2026-06-23 21:03:38', NULL, 1, NULL),
(5, 11, 'Mi tienda', 'Descripción de mi tienda', '#3b82f6', 'moderna', NULL, NULL, NULL, NULL, NULL, 1, '2026-07-24 09:27:34', '2026-07-24 09:28:19', 1, NULL),
(6, 12, 'Mi tienda', 'Descripción de mi tienda', '#c9a84c', 'moderna', NULL, NULL, NULL, NULL, NULL, 1, '2026-08-27 11:51:09', NULL, 1, NULL);

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `usuarios`
--

CREATE TABLE `usuarios` (
  `id` int(11) NOT NULL,
  `nombre` varchar(120) NOT NULL,
  `correo` varchar(180) NOT NULL,
  `contrasena` varchar(255) DEFAULT NULL,
  `rol` enum('cliente','emprendedor','administrador') NOT NULL DEFAULT 'cliente',
  `foto` varchar(300) DEFAULT NULL COMMENT 'Ruta de foto de perfil',
  `telefono` varchar(20) DEFAULT NULL,
  `ciudad` varchar(100) DEFAULT NULL,
  `direccion` varchar(255) DEFAULT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `creado_en` datetime NOT NULL DEFAULT current_timestamp(),
  `google_id` varchar(255) DEFAULT NULL,
  `reset_token` varchar(64) DEFAULT NULL,
  `reset_expira` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Volcado de datos para la tabla `usuarios`
--

INSERT INTO `usuarios` (`id`, `nombre`, `correo`, `contrasena`, `rol`, `foto`, `telefono`, `ciudad`, `direccion`, `activo`, `creado_en`, `google_id`, `reset_token`, `reset_expira`) VALUES
(1, 'María Emprendedora', 'emprendedor@cabore.co', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uFutHmfSa', 'emprendedor', NULL, NULL, NULL, NULL, 1, '2026-06-18 19:59:21', NULL, NULL, NULL),
(2, 'Carlos Comprador', 'cliente@cabore.co', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uFutHmfSa', 'cliente', NULL, NULL, NULL, NULL, 1, '2026-06-18 19:59:21', NULL, NULL, NULL),
(3, 'Diana Reyes', 'dianareyes100308@gmail.com', '$2y$10$MSWx9KuDIUVbAseQb/Afg.VLrzN0t3TVH9onTm1TI1P9oPiPmarfq', 'emprendedor', NULL, NULL, NULL, NULL, 1, '2026-06-19 16:36:27', NULL, '496ad036af7e6fa092bbc1234c6c31288f7c41b20a9e88f1c123dd7cac38b0b5', '2026-09-13 20:55:54'),
(6, 'Miguel Bonilla', 'miguelbonilla@gmail.com', '$2y$10$8WEmDVoXxqOREZxiVYlq7Oqa7up5x1UPUylnIqazwOgkMKFVy10h.', 'cliente', NULL, NULL, NULL, NULL, 1, '2026-06-19 17:09:46', NULL, NULL, NULL),
(7, 'diana reyes', 'dianareyes103008@gmail.com', '$2y$10$KsOgJ4EhRyfR32LBll8GTely/Pldu2KT4mUKOog6rKKXqpJf1.GjK', 'cliente', NULL, '3134679909', NULL, 'carrera 80d #60-13 sur', 1, '2026-06-21 18:33:40', NULL, '8cba1e844aa3bfe990bbaf3b0dc84ee209448b82b58052aa07d1c0d2f9994587', '2026-09-13 21:04:50'),
(8, 'Miguel Bonilla', 'miguel1234@gmail.com', '$2y$10$khu434X/SFwOwYlhLYtYru/n2LqfELQ6rlf73Z4JJiXTGY5chAY0G', 'cliente', NULL, NULL, NULL, NULL, 1, '2026-06-21 21:17:52', NULL, NULL, NULL),
(9, 'yulieth carvajal', 'yuliethcarvajal@gmail.com', '$2y$10$laUh0lheCGnebNZowOshUucIntZSRIPkm.SnH.SrTT41nPV7sHmv2', 'emprendedor', NULL, NULL, NULL, NULL, 1, '2026-06-23 14:12:42', NULL, NULL, NULL),
(10, 'Viviana reyes', 'vivianareyes@gmail.com', '$2y$10$fiqz7s5IhZ.WGDowUPeTfOgBMGQzFiA814DTvcRKGKi1xZrdcgH02', 'emprendedor', NULL, NULL, NULL, NULL, 1, '2026-06-23 21:03:38', NULL, NULL, NULL),
(11, 'NATALIA REYES', 'NATALIA@GMAIL.COM', '$2y$10$tETbqIIKHtzMLrfsTx47YeCTQkLFrw.G9Rf4FjQgA0HhWIMkaLPFu', 'emprendedor', NULL, NULL, NULL, NULL, 1, '2026-07-24 09:27:34', NULL, NULL, NULL),
(12, 'mariana cala', 'marianacal@gmail.com', '$2y$10$T5gaUBuidWbC4AzZ5WyOeO4pyp7HghpQW1a1cqwkAA4o38FV/qQpO', 'emprendedor', NULL, NULL, NULL, NULL, 0, '2026-08-27 11:51:09', NULL, NULL, NULL),
(13, 'Diana Reyes', 'dianareyec03@gmail.com', '$2y$10$Nse5HdIAv7/4uzc3nx.NnOEKC5IZLneJ6TyiieKkfVtqCURtHj4F6', 'administrador', NULL, NULL, NULL, NULL, 1, '2026-09-12 22:44:45', NULL, NULL, NULL),
(14, 'Diana Reyes', 'diana.reyes103008@gmail.com', NULL, 'cliente', NULL, NULL, NULL, NULL, 0, '2026-09-13 11:44:56', '108498717422218801002', '219b93fa1c54a618f81f840ced19554a5f6835114f5eba0aa21792809e87bb65', '2026-09-14 20:46:02'),
(15, 'Yulieth Carvajal', 'yuliethcarvajal2008@gmail.com', '$2y$10$5S9Z3Mo0tbJraX.eeN3SwuuNXgd3V55AZ8sQSkwDrHB9iRw0oZKcO', 'administrador', NULL, NULL, NULL, NULL, 1, '2026-09-14 12:34:13', NULL, NULL, NULL),
(16, 'Miguel Bonilla', 'mibosari44@gmail.com', '$2y$10$YSxJOHJKah2PYWLu00m1mOBeXKgmj.Ln0xgo4Tr9IbJS.q71QlIfm', 'administrador', NULL, NULL, NULL, NULL, 1, '2026-09-14 12:34:47', NULL, NULL, NULL);

--
-- Índices para tablas volcadas
--

--
-- Indices de la tabla `categorias`
--
ALTER TABLE `categorias`
  ADD PRIMARY KEY (`id`);

--
-- Indices de la tabla `conversaciones`
--
ALTER TABLE `conversaciones`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uniq_tienda_cliente` (`tienda_id`,`cliente_id`),
  ADD KEY `fk_conv_cliente` (`cliente_id`);

--
-- Indices de la tabla `favoritos`
--
ALTER TABLE `favoritos`
  ADD PRIMARY KEY (`usuario_id`,`producto_id`),
  ADD KEY `fk_fav_producto` (`producto_id`);

--
-- Indices de la tabla `mensajes`
--
ALTER TABLE `mensajes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `fk_msg_conversacion` (`conversacion_id`),
  ADD KEY `fk_msg_emisor` (`emisor_id`);

--
-- Indices de la tabla `pedidos`
--
ALTER TABLE `pedidos`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_pedido_usuario` (`usuario_id`);

--
-- Indices de la tabla `pedido_items`
--
ALTER TABLE `pedido_items`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_item_pedido` (`pedido_id`),
  ADD KEY `idx_item_producto` (`producto_id`);

--
-- Indices de la tabla `productos`
--
ALTER TABLE `productos`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_tienda` (`tienda_id`),
  ADD KEY `idx_categoria` (`categoria_id`),
  ADD KEY `idx_activo` (`activo`),
  ADD KEY `idx_destacado` (`destacado`),
  ADD KEY `idx_productos_estado_verificacion` (`estado_verificacion`),
  ADD KEY `fk_productos_revisor` (`revisado_por`);

--
-- Indices de la tabla `resenas`
--
ALTER TABLE `resenas`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_resena` (`producto_id`,`usuario_id`),
  ADD KEY `fk_resena_usuario` (`usuario_id`);

--
-- Indices de la tabla `tiendas`
--
ALTER TABLE `tiendas`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_usuario` (`usuario_id`);

--
-- Indices de la tabla `usuarios`
--
ALTER TABLE `usuarios`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `correo` (`correo`),
  ADD UNIQUE KEY `google_id` (`google_id`),
  ADD KEY `idx_correo` (`correo`),
  ADD KEY `idx_rol` (`rol`);

--
-- AUTO_INCREMENT de las tablas volcadas
--

--
-- AUTO_INCREMENT de la tabla `categorias`
--
ALTER TABLE `categorias`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=13;

--
-- AUTO_INCREMENT de la tabla `conversaciones`
--
ALTER TABLE `conversaciones`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

--
-- AUTO_INCREMENT de la tabla `mensajes`
--
ALTER TABLE `mensajes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=4;

--
-- AUTO_INCREMENT de la tabla `pedidos`
--
ALTER TABLE `pedidos`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=5;

--
-- AUTO_INCREMENT de la tabla `pedido_items`
--
ALTER TABLE `pedido_items`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=5;

--
-- AUTO_INCREMENT de la tabla `productos`
--
ALTER TABLE `productos`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=5;

--
-- AUTO_INCREMENT de la tabla `resenas`
--
ALTER TABLE `resenas`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de la tabla `tiendas`
--
ALTER TABLE `tiendas`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=7;

--
-- AUTO_INCREMENT de la tabla `usuarios`
--
ALTER TABLE `usuarios`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=17;

--
-- Restricciones para tablas volcadas
--

--
-- Filtros para la tabla `conversaciones`
--
ALTER TABLE `conversaciones`
  ADD CONSTRAINT `fk_conv_cliente` FOREIGN KEY (`cliente_id`) REFERENCES `usuarios` (`id`),
  ADD CONSTRAINT `fk_conv_tienda` FOREIGN KEY (`tienda_id`) REFERENCES `tiendas` (`id`);

--
-- Filtros para la tabla `favoritos`
--
ALTER TABLE `favoritos`
  ADD CONSTRAINT `fk_fav_producto` FOREIGN KEY (`producto_id`) REFERENCES `productos` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_fav_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE;

--
-- Filtros para la tabla `mensajes`
--
ALTER TABLE `mensajes`
  ADD CONSTRAINT `fk_msg_conversacion` FOREIGN KEY (`conversacion_id`) REFERENCES `conversaciones` (`id`),
  ADD CONSTRAINT `fk_msg_emisor` FOREIGN KEY (`emisor_id`) REFERENCES `usuarios` (`id`);

--
-- Filtros para la tabla `pedidos`
--
ALTER TABLE `pedidos`
  ADD CONSTRAINT `fk_pedido_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE;

--
-- Filtros para la tabla `pedido_items`
--
ALTER TABLE `pedido_items`
  ADD CONSTRAINT `fk_item_pedido` FOREIGN KEY (`pedido_id`) REFERENCES `pedidos` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_item_producto` FOREIGN KEY (`producto_id`) REFERENCES `productos` (`id`) ON DELETE CASCADE;

--
-- Filtros para la tabla `productos`
--
ALTER TABLE `productos`
  ADD CONSTRAINT `fk_producto_categoria` FOREIGN KEY (`categoria_id`) REFERENCES `categorias` (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_producto_tienda` FOREIGN KEY (`tienda_id`) REFERENCES `tiendas` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_productos_revisor` FOREIGN KEY (`revisado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL;

--
-- Filtros para la tabla `resenas`
--
ALTER TABLE `resenas`
  ADD CONSTRAINT `fk_resena_producto` FOREIGN KEY (`producto_id`) REFERENCES `productos` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_resena_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE;

--
-- Filtros para la tabla `tiendas`
--
ALTER TABLE `tiendas`
  ADD CONSTRAINT `fk_tienda_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
