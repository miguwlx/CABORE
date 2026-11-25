-- ============================================================
-- CABORE — Constructor Avanzado de Tiendas Personalizables
-- Tabla: tienda_personalizacion (relación 1 a 1 con tiendas)
--
-- Se ejecuta automáticamente al iniciar Spring Boot en modo dev
-- (CREATE TABLE IF NOT EXISTS), sin alterar ni borrar datos de
-- cabore_restaurada.
-- ============================================================

CREATE TABLE IF NOT EXISTS tienda_personalizacion (
    id                        INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tienda_id                 INT NOT NULL,
    eslogan                   VARCHAR(200) NULL,
    favicon                   VARCHAR(255) NULL,
    plantilla                 VARCHAR(40)  NOT NULL DEFAULT 'moderna',
    color_primario            VARCHAR(20)  NOT NULL DEFAULT '#c9a84c',
    color_secundario          VARCHAR(20)  NOT NULL DEFAULT '#e2603f',
    color_fondo               VARCHAR(20)  NOT NULL DEFAULT '#faf7f0',
    color_superficie          VARCHAR(20)  NOT NULL DEFAULT '#ffffff',
    color_texto               VARCHAR(20)  NOT NULL DEFAULT '#1c1810',
    color_botones             VARCHAR(20)  NOT NULL DEFAULT '#c9a84c',
    color_texto_botones       VARCHAR(20)  NOT NULL DEFAULT '#0d0d14',
    color_encabezado          VARCHAR(20)  NOT NULL DEFAULT '#ffffff',
    fuente_titulos            VARCHAR(80)  NOT NULL DEFAULT 'Playfair Display',
    fuente_cuerpo             VARCHAR(80)  NOT NULL DEFAULT 'DM Sans',
    tamano_texto              VARCHAR(20)  NOT NULL DEFAULT 'normal',
    estilo_tarjeta            VARCHAR(30)  NOT NULL DEFAULT 'elevada',
    distribucion_productos    VARCHAR(30)  NOT NULL DEFAULT 'cuadricula',
    columnas_productos        INT          NOT NULL DEFAULT 3,
    radio_bordes              VARCHAR(20)  NOT NULL DEFAULT 'suave',
    nivel_sombra              VARCHAR(20)  NOT NULL DEFAULT 'media',
    espaciado                 VARCHAR(20)  NOT NULL DEFAULT 'normal',
    estilo_encabezado         VARCHAR(30)  NOT NULL DEFAULT 'centrado',
    estilo_pie                VARCHAR(30)  NOT NULL DEFAULT 'completo',
    portada_diseno            VARCHAR(30)  NOT NULL DEFAULT 'impacto',
    portada_titulo            VARCHAR(180) NULL,
    portada_subtitulo         VARCHAR(220) NULL,
    portada_descripcion       TEXT         NULL,
    portada_boton_texto       VARCHAR(80)  NULL,
    portada_boton_url         VARCHAR(255) NULL,
    portada_boton2_texto      VARCHAR(80)  NULL,
    portada_boton2_url        VARCHAR(255) NULL,
    portada_alineacion        VARCHAR(20)  NOT NULL DEFAULT 'centro',
    portada_overlay           INT          NOT NULL DEFAULT 40,
    mostrar_precios           TINYINT(1)   NOT NULL DEFAULT 1,
    mostrar_descuentos        TINYINT(1)   NOT NULL DEFAULT 1,
    mostrar_calificaciones    TINYINT(1)   NOT NULL DEFAULT 1,
    mostrar_etiquetas         TINYINT(1)   NOT NULL DEFAULT 1,
    mostrar_descripcion_prod  TINYINT(1)   NOT NULL DEFAULT 1,
    estilo_boton_compra       VARCHAR(30)  NOT NULL DEFAULT 'solido',
    texto_boton_compra        VARCHAR(60)  NOT NULL DEFAULT 'Agregar al carrito',
    orden_predeterminado      VARCHAR(30)  NOT NULL DEFAULT 'reciente',
    facebook                  VARCHAR(180) NULL,
    tiktok                    VARCHAR(180) NULL,
    telefono_contacto         VARCHAR(60)  NULL,
    correo_contacto           VARCHAR(160) NULL,
    horario_atencion          VARCHAR(220) NULL,
    historia_tienda           TEXT         NULL,
    politica_envios           TEXT         NULL,
    politica_devoluciones     TEXT         NULL,
    secciones_json            TEXT         NULL,
    galeria_json              TEXT         NULL,
    testimonios_json          TEXT         NULL,
    config_publicada          LONGTEXT     NULL,
    config_borrador           LONGTEXT     NULL,
    tiene_borrador            TINYINT(1)   NOT NULL DEFAULT 0,
    actualizado_en            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tienda_personalizacion_tienda UNIQUE (tienda_id)
);

CREATE INDEX IF NOT EXISTS idx_tienda_pers_tienda ON tienda_personalizacion (tienda_id);
