-- ============================================================
-- CABORE — FASE 3 (Módulo del cliente / DIANA)
-- Tabla nueva: carrito de compras PERSISTENTE por usuario.
--
-- Se ejecuta automáticamente al arrancar la app (spring.sql.init
-- con CREATE TABLE IF NOT EXISTS), por lo que es idempotente:
-- puede correr sobre una base que ya tenga cargado cabore.sql
-- sin dañar nada. Cuando Miguel consolide el esquema en cabore.sql,
-- basta con copiar este bloque tal cual.
--
-- Reglas aplicadas:
--   * Una sola fila por (usuario, producto): la cantidad vive en
--     la columna `cantidad`.
--   * El precio NO se guarda aquí: siempre se lee de productos al
--     listar y se congela en pedido_items al comprar (anti-manipulación).
--   * Sin claves foráneas para no interferir con la carga de dumps
--     en entornos donde aun no existan usuarios/productos.
-- ============================================================

CREATE TABLE IF NOT EXISTS carrito_items (
    id           INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    usuario_id   INT NOT NULL,
    producto_id  INT NOT NULL,
    cantidad     INT NOT NULL DEFAULT 1,
    agregado_en  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_carrito_usuario_producto UNIQUE (usuario_id, producto_id)
);

CREATE INDEX IF NOT EXISTS idx_carrito_usuario ON carrito_items (usuario_id);
