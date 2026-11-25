package co.cabore.CaboreApplication.emprendedor;

import co.cabore.CaboreApplication.config.UsuarioPrincipal;
import co.cabore.CaboreApplication.model.*;
import co.cabore.CaboreApplication.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FASE 3 — Módulo Emprendedor y Tienda Pública (Miguel).
 * Pruebas integrales de extremo a extremo: dashboard, estadísticas de ventas,
 * producto estrella, ranking, gestión de productos con imágenes, pedidos recibidos,
 * actualización de estado, reportes, exportación CSV, facturas filtradas por tienda,
 * personalización y tienda pública con las 4 plantillas originales.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Fase3EmprendedorTiendaIntegrationTest {

    /** Firma binaria mínima válida de un PNG (1x1). */
    private static final byte[] PNG_VALIDO = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
    };

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired TiendaRepository tiendas;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    @Autowired PedidoRepository pedidos;
    @Autowired TiendaPersonalizacionRepository personalizaciones;

    Usuario emp1, emp2, cliente;
    Tienda tienda1, tienda2;
    Categoria catModa;
    Producto prodEstrella, prodSecundario, prodOtraTienda;
    Pedido pedidoMixto, pedidoAjeno;

    @BeforeEach
    void seed() {
        emp1 = crearUsuario("María Emprendedora", "maria@test.co", Rol.emprendedor);
        emp2 = crearUsuario("Pedro Otro", "pedro@test.co", Rol.emprendedor);
        cliente = crearUsuario("Ana Compradora", "ana@test.co", Rol.cliente);
        cliente.setTelefono("3009998877");
        usuarios.save(cliente);

        catModa = new Categoria();
        catModa.setNombre("Ropa y Moda");
        catModa.setIcono("👗");
        catModa = categorias.save(catModa);

        tienda1 = new Tienda();
        tienda1.setUsuario(emp1);
        tienda1.setNombre("Boutique María");
        tienda1.setDescripcion("Estilo artesanal único");
        tienda1.setColor("#c9a84c");
        tienda1.setPlantilla("moderna");
        tienda1.setActivo(true);
        tienda1 = tiendas.save(tienda1);

        tienda2 = new Tienda();
        tienda2.setUsuario(emp2);
        tienda2.setNombre("Tienda de Pedro");
        tienda2.setDescripcion("Accesorios Pedro");
        tienda2.setActivo(true);
        tienda2 = tiendas.save(tienda2);

        prodEstrella = crearProducto("Vestido Primavera", "100000.00", "80000.00", 10, true, tienda1);
        prodSecundario = crearProducto("Blusa Lino", "50000.00", null, 5, false, tienda1);
        prodOtraTienda = crearProducto("Reloj de Pedro", "200000.00", null, 4, false, tienda2);

        // Pedido mixto: 3× Vestido Primavera (240.000 de tienda1) + 1× Reloj de Pedro (200.000 de tienda2)
        pedidoMixto = new Pedido();
        pedidoMixto.setUsuario(cliente);
        pedidoMixto.setEstado(PedidoEstado.pendiente);
        pedidoMixto.setDireccion("Calle 50 #10-20");
        pedidoMixto.setTelefonoContacto("3009998877");
        pedidoMixto.setTotal(new BigDecimal("440000.00"));

        PedidoItem it1 = new PedidoItem();
        it1.setProducto(prodEstrella);
        it1.setCantidad(3);
        it1.setPrecioUnit(new BigDecimal("80000.00"));
        it1.setPrecioUnitario(new BigDecimal("80000.00"));
        it1.setSubtotal(new BigDecimal("240000.00"));
        pedidoMixto.addItem(it1);

        PedidoItem it2 = new PedidoItem();
        it2.setProducto(prodOtraTienda);
        it2.setCantidad(1);
        it2.setPrecioUnit(new BigDecimal("200000.00"));
        it2.setPrecioUnitario(new BigDecimal("200000.00"));
        it2.setSubtotal(new BigDecimal("200000.00"));
        pedidoMixto.addItem(it2);

        pedidoMixto = pedidos.save(pedidoMixto);

        // Pedido exclusivo de tienda2
        pedidoAjeno = new Pedido();
        pedidoAjeno.setUsuario(cliente);
        pedidoAjeno.setEstado(PedidoEstado.pendiente);
        pedidoAjeno.setDireccion("Cra 7 #100-10");
        pedidoAjeno.setTotal(new BigDecimal("200000.00"));
        PedidoItem it3 = new PedidoItem();
        it3.setProducto(prodOtraTienda);
        it3.setCantidad(1);
        it3.setPrecioUnit(new BigDecimal("200000.00"));
        it3.setPrecioUnitario(new BigDecimal("200000.00"));
        it3.setSubtotal(new BigDecimal("200000.00"));
        pedidoAjeno.addItem(it3);
        pedidoAjeno = pedidos.save(pedidoAjeno);
    }

    /* ── 1. Acceso, auto-creación de tienda y dashboard de ventas ──────── */

    @Test
    void dashboardMuestraVentasProductoEstrellaRankingYAutoCreaTiendaSiFalta() throws Exception {
        // Cliente no puede entrar a /emprendedor
        mvc.perform(get("/emprendedor").with(como(cliente))).andExpect(status().isForbidden());

        // Emprendedor sin tienda previa: se le crea automáticamente al abrir /emprendedor
        Usuario empSinTienda = crearUsuario("Laura Nueva", "laura@test.co", Rol.emprendedor);
        mvc.perform(get("/emprendedor").with(como(empSinTienda)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mi tienda")));
        assertThat(tiendas.findByUsuarioId(empSinTienda.getId())).isPresent();

        // Dashboard de María: muestra solo los $240.000 de sus productos (no los $200.000 de Pedro),
        // el Producto Estrella "Vestido Primavera" y el comprador "Ana Compradora"
        mvc.perform(get("/emprendedor").with(como(emp1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Boutique María")))
                .andExpect(content().string(containsString("Producto estrella")))
                .andExpect(content().string(containsString("Vestido Primavera")))
                .andExpect(content().string(containsString("$240.000")))
                .andExpect(content().string(containsString("Ana Compradora")));
    }

    /* ── 2. Gestión de productos con imagen, oferta, categoría y moderación ── */

    @Test
    void crearYEditarProductoConImagenOfertaYReenvioAModeracion() throws Exception {
        MockMultipartFile imgValida = new MockMultipartFile(
                "imagen", "bolso.png", "image/png", PNG_VALIDO);

        mvc.perform(multipart("/emprendedor/productos")
                        .file(imgValida)
                        .param("formCompleto", "1")
                        .param("nombre", "Bolso Wayuu")
                        .param("descripcion", "Tejido a mano")
                        .param("precio", "120000")
                        .param("precio_oferta", "95000")
                        .param("categoria_id", catModa.getId().toString())
                        .param("stock", "7")
                        .param("destacado", "true")
                        .param("activo", "true")
                        .with(como(emp1)))
                .andExpect(status().is3xxRedirection());

        Producto creado = productos.findByTiendaIdOrderByIdDesc(tienda1.getId()).stream()
                .filter(p -> p.getNombre().equals("Bolso Wayuu"))
                .findFirst().orElseThrow();
        assertThat(creado.getEstadoVerificacion()).isEqualTo(EstadoVerificacion.pendiente);
        assertThat(creado.getPrecio()).isEqualByComparingTo("120000.00");
        assertThat(creado.getPrecioOferta()).isEqualByComparingTo("95000.00");
        assertThat(creado.isDestacado()).isTrue();
        assertThat(creado.getImagen()).startsWith("img/productos/prod_").endsWith(".png");
        assertThat(creado.getRutaImagen()).startsWith("/img/productos/prod_");

        // Archivo falso con extensión .png pero contenido de texto es rechazado
        MockMultipartFile archivoFalso = new MockMultipartFile(
                "imagen", "script.png", "image/png", "<?php echo 1; ?>".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/emprendedor/productos")
                        .file(archivoFalso)
                        .param("nombre", "Producto Malicioso")
                        .param("precio", "10000")
                        .param("stock", "1")
                        .with(como(emp1)))
                .andExpect(status().is3xxRedirection());
        assertThat(productos.findByTiendaIdOrderByIdDesc(tienda1.getId()))
                .noneMatch(p -> p.getNombre().equals("Producto Malicioso"));

        // Si el admin rechaza el producto y el emprendedor lo edita, vuelve a estado pendiente
        creado.setEstadoVerificacion(EstadoVerificacion.rechazado);
        creado.setMotivoRechazo("Foto oscura");
        productos.saveAndFlush(creado);

        mvc.perform(multipart("/emprendedor/productos/" + creado.getId())
                        .param("formCompleto", "1")
                        .param("nombre", "Bolso Wayuu Corregido")
                        .param("descripcion", "Nueva descripción clara")
                        .param("precio", "120000")
                        .param("precio_oferta", "90000")
                        .param("categoria_id", catModa.getId().toString())
                        .param("stock", "8")
                        .param("destacado", "true")
                        .param("activo", "true")
                        .with(como(emp1)))
                .andExpect(status().is3xxRedirection());

        Producto editado = productos.findById(creado.getId()).orElseThrow();
        assertThat(editado.getNombre()).isEqualTo("Bolso Wayuu Corregido");
        assertThat(editado.getEstadoVerificacion()).isEqualTo(EstadoVerificacion.pendiente);
        assertThat(editado.getMotivoRechazo()).isNull();

        // Emprendedor 2 NO puede editar el producto de Emprendedor 1
        mvc.perform(get("/emprendedor/productos/" + creado.getId() + "/editar").with(como(emp2)))
                .andExpect(status().isForbidden());
    }

    /* ── 3. Gestión de pedidos, cambio de estado, reportes, CSV y facturas ── */

    @Test
    void actualizarEstadoDePedidoReportesCsvYFacturaFiltradaPorTienda() throws Exception {
        // María puede cambiar el estado del pedido que incluye productos suyos
        mvc.perform(post("/emprendedor/pedidos/estado/ajax")
                        .param("pedido_id", pedidoMixto.getId().toString())
                        .param("estado", "enviado")
                        .with(como(emp1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.estado").value("enviado"));
        assertThat(pedidos.findById(pedidoMixto.getId()).orElseThrow().getEstado()).isEqualTo(PedidoEstado.enviado);

        // María NO puede cambiar el estado de un pedido que solo tiene productos de Pedro
        mvc.perform(post("/emprendedor/pedidos/estado/ajax")
                        .param("pedido_id", pedidoAjeno.getId().toString())
                        .param("estado", "cancelado")
                        .with(como(emp1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error").value("Este pedido no pertenece a tu tienda."));
        assertThat(pedidos.findById(pedidoAjeno.getId()).orElseThrow().getEstado()).isEqualTo(PedidoEstado.pendiente);

        // La factura del pedido mixto vista por María muestra SOLO su producto ($240.000) y NO el reloj de Pedro
        mvc.perform(get("/emprendedor/factura/" + pedidoMixto.getId()).with(como(emp1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vestido Primavera")))
                .andExpect(content().string(containsString("$240.000")))
                .andExpect(content().string(not(containsString("Reloj de Pedro"))));

        // María no puede abrir la factura de un pedido sin productos suyos
        mvc.perform(get("/emprendedor/factura/" + pedidoAjeno.getId()).with(como(emp1)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/emprendedor?tab=facturas"));

        // Exportación CSV de reportes de la tienda
        byte[] csvBytes = mvc.perform(get("/emprendedor/reportes/csv")
                        .param("desde", LocalDate.now().minusDays(7).toString())
                        .param("hasta", LocalDate.now().toString())
                        .with(como(emp1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFPedido,Fecha,Cliente,Correo,Productos,Estado,Total Tienda,Dirección");
        assertThat(csv).contains("3x Vestido Primavera").contains("240000.00").doesNotContain("Reloj de Pedro");
    }

    /* ── 4. Personalización y Tienda Pública con las 4 plantillas ──────── */

    @Test
    void personalizacionDeTiendaYAccesoPublicoALasCuatroPlantillas() throws Exception {
        // 1) Actualizar información (nombre, descripción, ciudad)
        mvc.perform(post("/emprendedor/tienda")
                        .param("seccion", "info")
                        .param("nombre", "Atelier María")
                        .param("descripcion", "Moda sostenible colombiana")
                        .param("ciudad", "Medellín")
                        .with(como(emp1)))
                .andExpect(status().is3xxRedirection());

        // 2) Actualizar contacto (WhatsApp e Instagram)
        mvc.perform(post("/emprendedor/tienda")
                        .param("seccion", "contacto")
                        .param("whatsapp", "+57 310-555-1234")
                        .param("instagram", "@atelier.maria")
                        .with(como(emp1)))
                .andExpect(status().is3xxRedirection());

        // 3) Probar las 4 plantillas originales + color + logo + banner y verificar en la tienda pública (sin login)
        MockMultipartFile logo = new MockMultipartFile("logo", "logo.png", "image/png", PNG_VALIDO);
        MockMultipartFile banner = new MockMultipartFile("banner", "banner.png", "image/png", PNG_VALIDO);

        for (String plantilla : Tienda.PLANTILLAS) {
            mvc.perform(multipart("/emprendedor/tienda")
                            .file(logo)
                            .file(banner)
                            .param("seccion", "apariencia")
                            .param("color", "#ec4899")
                            .param("plantilla", plantilla)
                            .with(como(emp1)))
                    .andExpect(status().is3xxRedirection());

            // Acceso PÚBLICO (sin sesión) a /tienda/{id}
            mvc.perform(get("/tienda/" + tienda1.getId()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("plantilla-" + plantilla)))
                    .andExpect(content().string(containsString("--brand:#ec4899")))
                    .andExpect(content().string(containsString("Atelier María")))
                    .andExpect(content().string(containsString("Medellín")))
                    .andExpect(content().string(containsString("https://wa.me/573105551234")))
                    .andExpect(content().string(containsString("https://instagram.com/atelier.maria")))
                    .andExpect(content().string(containsString("/img/logos/logo_")))
                    .andExpect(content().string(containsString("/img/banners/banner_")))
                    .andExpect(content().string(containsString("Vestido Primavera")));
        }
    }

    @Test
    void accionesRapidasDeInventarioYRestauracionDeStockAlCancelarPedidoPorEmprendedor() throws Exception {
        // 1) Actualización rápida de stock por AJAX
        mvc.perform(post("/emprendedor/productos/stock/ajax")
                        .param("producto_id", prodSecundario.getId().toString())
                        .param("stock", "25")
                        .with(como(emp1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.stock").value(25));
        assertThat(productos.findById(prodSecundario.getId()).orElseThrow().getStock()).isEqualTo(25);

        // Emprendedor ajeno NO puede modificar stock de tienda1
        mvc.perform(post("/emprendedor/productos/stock/ajax")
                        .param("producto_id", prodSecundario.getId().toString())
                        .param("stock", "0")
                        .with(como(emp2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false));
        assertThat(productos.findById(prodSecundario.getId()).orElseThrow().getStock()).isEqualTo(25);

        // 2) Alternar rápido campo destacado y activo
        mvc.perform(post("/emprendedor/productos/toggle/ajax")
                        .param("producto_id", prodSecundario.getId().toString())
                        .param("campo", "destacado")
                        .with(como(emp1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.destacado").value(true));
        assertThat(productos.findById(prodSecundario.getId()).orElseThrow().isDestacado()).isTrue();

        // 3) Al cancelar un pedido desde el panel del emprendedor, se restaura el stock automáticamente
        int stockAntes = productos.findById(prodEstrella.getId()).orElseThrow().getStock();
        mvc.perform(post("/emprendedor/pedidos/estado/ajax")
                        .param("pedido_id", pedidoMixto.getId().toString())
                        .param("estado", "cancelado")
                        .with(como(emp1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.estado").value("cancelado"));

        // pedidoMixto tenía 3 unidades de prodEstrella -> stock sube en 3
        assertThat(productos.findById(prodEstrella.getId()).orElseThrow().getStock()).isEqualTo(stockAntes + 3);

        // Un pedido cancelado no puede volver a cambiar de estado
        mvc.perform(post("/emprendedor/pedidos/estado/ajax")
                        .param("pedido_id", pedidoMixto.getId().toString())
                        .param("estado", "confirmado")
                        .with(como(emp1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error").value("Un pedido cancelado no puede cambiar de estado."));
    }

    /* ── 6. Constructor Avanzado de Tiendas Personalizables (Borrador, Publicación, Secciones y Vista Pública) ── */

    @Test
    void constructorAvanzadoGuardaBorradorPublicaPersonalizacionCompletaYRestaura() throws Exception {
        // 1) Cliente no puede acceder al constructor (/emprendedor/constructor -> 403)
        mvc.perform(get("/emprendedor/constructor").with(como(cliente)))
                .andExpect(status().isForbidden());

        // 2) Emprendedor carga el editor visual en tiempo real
        mvc.perform(get("/emprendedor/constructor").with(como(emp1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Studio")))
                .andExpect(content().string(containsString("Identidad de la tienda")))
                .andExpect(content().string(containsString("Constructor de secciones")));

        // 3) Subir imagen para la galería desde el constructor
        MockMultipartFile fotoGaleria = new MockMultipartFile("archivo", "galeria1.png", "image/png", PNG_VALIDO);
        mvc.perform(multipart("/emprendedor/constructor/imagen")
                        .file(fotoGaleria)
                        .param("tipo", "galeria")
                        .with(como(emp1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.ruta").exists());

        // 4) Guardar BORRADOR: se persiste en tienda_personalizacion sin alterar aún la tienda pública para visitantes
        String jsonBorrador = """
                {
                  "nombre": "Atelier María Borrador",
                  "eslogan": "Eslogan en borrador privado",
                  "descripcion": "Descripción en borrador",
                  "ciudad": "Cartagena",
                  "plantilla": "tecnologica",
                  "colorPrimario": "#38bdf8",
                  "colorSecundario": "#6366f1",
                  "colorFondo": "#090d16",
                  "colorSuperficie": "#111827",
                  "colorTexto": "#f1f5f9",
                  "colorBotones": "#38bdf8",
                  "colorTextoBotones": "#090d16",
                  "colorEncabezado": "#0f172a",
                  "fuenteTitulos": "Cormorant Garamond",
                  "fuenteCuerpo": "Outfit",
                  "tamanoTexto": "grande",
                  "estiloTarjeta": "bordeada",
                  "distribucionProductos": "cuadricula",
                  "columnasProductos": 4,
                  "radioBordes": "redondeado",
                  "nivelSombra": "intensa",
                  "espaciado": "amplio",
                  "estiloEncabezado": "centrado",
                  "estiloPie": "completo",
                  "portadaDiseno": "dividida",
                  "portadaTitulo": "Colección Cyber Artesanal 2026",
                  "portadaSubtitulo": "Edición Limitada",
                  "portadaDescripcion": "Fusión de tradición y diseño contemporáneo",
                  "portadaBotonTexto": "Descubrir piezas",
                  "portadaBotonUrl": "#seccion-catalogo",
                  "portadaBoton2Texto": "Hablar con nosotros",
                  "portadaBoton2Url": "#seccion-contacto",
                  "portadaAlineacion": "izquierda",
                  "portadaOverlay": 50,
                  "mostrarPrecios": true,
                  "mostrarDescuentos": true,
                  "mostrarCalificaciones": true,
                  "mostrarEtiquetas": true,
                  "mostrarDescripcionProd": true,
                  "estiloBotonCompra": "capsula",
                  "textoBotonCompra": "Comprar ahora",
                  "ordenPredeterminado": "precio_asc",
                  "whatsapp": "3158889900",
                  "instagram": "@atelier.cyber",
                  "facebook": "ateliermaria.oficial",
                  "tiktok": "@atelier.tiktok",
                  "telefonoContacto": "(605) 660-1122",
                  "correoContacto": "hola@ateliermaria.co",
                  "horarioAtencion": "Lunes a Viernes 9am - 7pm",
                  "historiaTienda": "Nacimos en 2020 apoyando a más de 30 familias artesanas.",
                  "politicaEnvios": "Envío gratis en compras superiores a $150.000.",
                  "politicaDevoluciones": "Cambios sin costo durante 10 días.",
                  "testimonios": [
                    { "autor": "Camila Soto", "comentario": "Me encantó el acabado del vestido.", "calificacion": 5 }
                  ]
                }
                """;

        mvc.perform(post("/emprendedor/constructor/borrador")
                        .contentType("application/json")
                        .content(jsonBorrador)
                        .with(como(emp1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.tieneBorrador").value(true));

        TiendaPersonalizacion tpBorrador = personalizaciones.findByTiendaId(tienda1.getId()).orElseThrow();
        assertThat(tpBorrador.isTieneBorrador()).isTrue();

        // Un visitante anónimo en /tienda/{id} todavía ve la versión publicada original ("Boutique María")
        mvc.perform(get("/tienda/" + tienda1.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Boutique María")))
                .andExpect(content().string(not(containsString("Eslogan en borrador privado"))));

        // Pero la dueña (emp1) con ?preview=borrador SÍ ve el borrador en /tienda/{id}?preview=borrador
        mvc.perform(get("/tienda/" + tienda1.getId()).param("preview", "borrador").with(como(emp1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Eslogan en borrador privado")))
                .andExpect(content().string(containsString("Colección Cyber Artesanal 2026")));

        // Otro emprendedor (emp2) NO puede ver el borrador ajeno aunque pase ?preview=borrador
        mvc.perform(get("/tienda/" + tienda1.getId()).param("preview", "borrador").with(como(emp2)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Eslogan en borrador privado"))));

        // 5) PUBLICAR CAMBIOS: ahora la personalización completa se guarda como versión pública en MariaDB
        mvc.perform(post("/emprendedor/constructor/publicar")
                        .contentType("application/json")
                        .content(jsonBorrador.replace("Eslogan en borrador privado", "Alta Moda Artesanal Colombiana"))
                        .with(como(emp1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.tieneBorrador").value(false));

        // La tienda pública /tienda/{id} ahora refleja la plantilla tecnológica, colores, textos,
        // portada dividida, botón "Comprar ahora", redes (TikTok, Facebook, Instagram, WhatsApp),
        // historia, políticas y testimonio para todos los visitantes
        mvc.perform(get("/tienda/" + tienda1.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("plantilla-tecnologica")))
                .andExpect(content().string(containsString("--brand:#38bdf8")))
                .andExpect(content().string(containsString("Alta Moda Artesanal Colombiana")))
                .andExpect(content().string(containsString("Colección Cyber Artesanal 2026")))
                .andExpect(content().string(containsString("Comprar ahora")))
                .andExpect(content().string(containsString("https://tiktok.com/@atelier.tiktok")))
                .andExpect(content().string(containsString("https://facebook.com/ateliermaria.oficial")))
                .andExpect(content().string(containsString("Nacimos en 2020 apoyando a más de 30 familias artesanas.")))
                .andExpect(content().string(containsString("Camila Soto")));

        // 6) RESTAURAR valores predeterminados
        mvc.perform(post("/emprendedor/constructor/restaurar").with(como(emp1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        assertThat(tiendas.findById(tienda1.getId()).orElseThrow().getPlantillaSegura()).isEqualTo("moderna");
    }

    private static RequestPostProcessor como(Usuario u) {
        UsuarioPrincipal up = new UsuarioPrincipal(u);
        RequestPostProcessor auth = authentication(
                new UsernamePasswordAuthenticationToken(up, null, up.getAuthorities()));
        return request -> csrf().postProcessRequest(auth.postProcessRequest(request));
    }

    private Usuario crearUsuario(String nombre, String correo, Rol rol) {
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setCorreo(correo);
        u.setRol(rol);
        u.setActivo(true);
        return usuarios.save(u);
    }

    private Producto crearProducto(String nombre, String precio, String oferta, int stock,
                                   boolean destacado, Tienda t) {
        Producto p = new Producto();
        p.setNombre(nombre);
        p.setDescripcion("Descripción de " + nombre);
        p.setPrecio(new BigDecimal(precio));
        if (oferta != null) p.setPrecioOferta(new BigDecimal(oferta));
        p.setStock(stock);
        p.setCategoriaId(catModa.getId());
        p.setDestacado(destacado);
        p.setActivo(true);
        p.setEstadoVerificacion(EstadoVerificacion.aprobado);
        p.setTienda(t);
        return productos.save(p);
    }
}
