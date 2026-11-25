package co.cabore.CaboreApplication.cliente;

import co.cabore.CaboreApplication.config.UsuarioPrincipal;
import co.cabore.CaboreApplication.model.*;
import co.cabore.CaboreApplication.repository.*;
import co.cabore.CaboreApplication.service.CheckoutService;
import co.cabore.CaboreApplication.service.CarritoService;
import co.cabore.CaboreApplication.service.PerfilService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FASE 3 — Módulo del cliente (DIANA).
 * Integración end-to-end sobre H2 en modo MariaDB: catálogo, búsqueda y
 * filtros, carrito persistente, compra real (pedido + descuento de stock en
 * BD), cancelación con devolución de stock, favoritos, reseñas, perfil,
 * factura y protección por usuario autenticado.
 *
 * Cada test corre en una transacción que se revierte al terminar: los datos
 * sembrados no contaminan otras pruebas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Fase3ClienteIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired TiendaRepository tiendas;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    @Autowired PedidoRepository pedidos;
    @Autowired CarritoItemRepository carrito;
    @Autowired FavoritoRepository favoritos;
    @Autowired ResenaRepository resenas;
    @Autowired CheckoutService checkoutService;
    @Autowired CarritoService carritoService;
    @Autowired PerfilService perfilService;

    Usuario cliente1, cliente2, emprendedor;
    Tienda tienda;
    Producto vestido, collar;

    @BeforeEach
    void seed() {
        cliente1 = usuario("Ana Compra", "ana@test.co");
        cliente2 = usuario("Beto Compra", "beto@test.co");
        emprendedor = usuario("Luis Tienda", "luis@test.co");
        emprendedor.setRol(Rol.emprendedor);
        usuarios.save(emprendedor);

        Categoria ropa = new Categoria();
        ropa.setNombre("Ropa Prueba");
        ropa.setIcono("👗");
        ropa = categorias.save(ropa);

        tienda = new Tienda();
        tienda.setUsuario(emprendedor);
        tienda.setNombre("Boutique Prueba");
        tienda.setDescripcion("Tienda de pruebas");
        tienda.setColor("#c9a84c");
        tienda.setActivo(true);
        tienda = tiendas.save(tienda);

        vestido = producto("Vestido Aurora", 200000.0, 100000.0, 5, ropa.getId(), tienda);
        collar = producto("Collar Testigo", 50000.0, null, 2, null, tienda);
    }

    /* ── 1 y 2: catálogo + búsqueda y filtros ─────────────────────────── */

    @Test
    void catalogoMuestraProductosTiendasYDestacados() throws Exception {
        vestido.setDestacado(true);
        productos.save(vestido);

        mvc.perform(get("/cliente").with(como(cliente1)))
                .andExpect(status().isOk())
                .andExpect(view().name("cliente"))
                .andExpect(content().string(containsString("Vestido Aurora")))
                .andExpect(content().string(containsString("Collar Testigo")))
                .andExpect(content().string(containsString("Boutique Prueba")))
                .andExpect(content().string(containsString("Destacados para ti")));
    }

    @Test
    void busquedaFiltraPorNombreYDejaFueraLoDemas() throws Exception {
        mvc.perform(get("/cliente").param("q", "Aurora").with(como(cliente1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vestido Aurora")))
                .andExpect(content().string(not(containsString("Collar Testigo"))));
    }

    @Test
    void filtrosDeOfertaYRangoDePrecio() throws Exception {
        // Solo ofertas: únicamente el vestido (precio_oferta < precio)
        mvc.perform(get("/cliente").param("oferta", "1").with(como(cliente1)))
                .andExpect(content().string(containsString("Vestido Aurora")))
                .andExpect(content().string(not(containsString("Collar Testigo"))));

        // Rango de precio que excluye todo → estado vacío
        mvc.perform(get("/cliente").param("min", "1000000").with(como(cliente1)))
                .andExpect(content().string(containsString("Sin resultados")));
    }

    @Test
    void ordenPorPrecioAscendenteUsaElPrecioDeOferta() throws Exception {
        // Vestido vigente = 100.000 (oferta); Collar = 50.000 → collar primero.
        String html = mvc.perform(get("/cliente").param("orden", "precio_asc").with(como(cliente1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html.indexOf("Collar Testigo")).isLessThan(html.indexOf("Vestido Aurora"));
    }

    /* ── 3: carrito de compras persistente ─────────────────────────────── */

    @Test
    void carritoAgregaIncrementaYPersisteEnBaseDeDatos() throws Exception {
        mvc.perform(post("/cliente/carrito/agregar").param("productoId", vestido.getId().toString())
                .with(como(cliente1))).andExpect(status().is3xxRedirection());
        mvc.perform(post("/cliente/carrito/agregar").param("productoId", vestido.getId().toString())
                .with(como(cliente1))).andExpect(status().is3xxRedirection());

        List<CarritoItem> filas = carrito.findByUsuarioIdOrderByIdAsc(cliente1.getId());
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getCantidad()).isEqualTo(2);
        assertThat(filas.get(0).getUsuario().getId()).isEqualTo(cliente1.getId());

        // El JSON del drawer toma el precio desde la BD (oferta = 100.000 × 2)
        mvc.perform(get("/cliente/carrito/resumen").with(como(cliente1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.total").value(200000.0))
                .andExpect(jsonPath("$.items[0].nombre").value("Vestido Aurora"));
    }

    @Test
    void carritoLimitaCantidadAlStockReal() throws Exception {
        mvc.perform(post("/cliente/carrito/agregar")
                        .param("productoId", collar.getId().toString())
                        .param("cantidad", "99")
                        .with(como(cliente1)))
                .andExpect(status().is3xxRedirection());

        assertThat(carrito.findByUsuarioIdAndProductoId(cliente1.getId(), collar.getId())
                .orElseThrow().getCantidad()).isEqualTo(2); // stock real del collar
    }

    @Test
    void carritoNoSeMezclaEntreUsuarios() throws Exception {
        mvc.perform(post("/cliente/carrito/agregar").param("productoId", vestido.getId().toString())
                .with(como(cliente1)));
        assertThat(carrito.countByUsuarioId(cliente2.getId())).isZero();

        mvc.perform(get("/cliente/carrito/resumen").with(como(cliente2)))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void carritoAutoLimpiaProductosQueDejaronDeEstarDisponibles() throws Exception {
        CarritoItem trampa = new CarritoItem();
        trampa.setUsuario(cliente2);
        trampa.setProducto(collar);
        trampa.setCantidad(1);
        carrito.save(trampa);

        collar.setActivo(false);
        productos.save(collar);
        productos.flush();

        mvc.perform(get("/cliente/carrito").with(como(cliente2)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tu carrito está vacío")));
        assertThat(carrito.countByUsuarioId(cliente2.getId())).isZero();
    }

    /* ── 4: checkout y compras reales ─────────────────────────────────── */

    @Test
    void compraRealCreaPedidoDescuentaStockYLimpiaCarrito() throws Exception {
        mvc.perform(post("/cliente/carrito/agregar").param("productoId", vestido.getId().toString())
                .with(como(cliente1)));
        mvc.perform(post("/cliente/carrito/agregar")
                        .param("productoId", collar.getId().toString())
                        .param("cantidad", "2")
                        .with(como(cliente1)));

        var result = mvc.perform(post("/cliente/checkout")
                        .param("direccion", "Calle 1 #2-3, Prueba")
                        .param("telefono", "3130000000")
                        .param("notas", "Dejar en recepción")
                        .with(como(cliente1)))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).matches("/cliente/pedido/\\d+\\?nuevo=1");
        var m = java.util.regex.Pattern.compile("/pedido/(\\d+)").matcher(location);
        assertThat(m.find()).isTrue();
        int pedidoId = Integer.parseInt(m.group(1));

        Pedido pedido = pedidos.findById(pedidoId).orElseThrow();
        assertThat(pedido.getUsuario().getId()).isEqualTo(cliente1.getId());
        assertThat(pedido.getEstado()).isEqualTo(PedidoEstado.pendiente);
        assertThat(pedido.getDireccion()).isEqualTo("Calle 1 #2-3, Prueba");
        // 1×100.000 (oferta vigente del vestido) + 2×50.000 = 200.000
        assertThat(pedido.getTotal().doubleValue()).isEqualTo(200000.0);
        assertThat(pedido.getItems()).hasSize(2);
        assertThat(pedido.getItems()).allSatisfy(i -> {
            assertThat(i.getPrecioUnit().doubleValue()).isGreaterThan(0);
            // compatibilidad con la lectura legacy: precio_unitario también poblado
            assertThat(i.getPrecioUnitario().doubleValue()).isEqualTo(i.getPrecioUnit().doubleValue());
            assertThat(i.getSubtotal().doubleValue())
                    .isEqualTo(i.getPrecioUnit().doubleValue() * i.getCantidad());
        });

        // STOCK DESCONTADO DE VERDAD en la tabla productos
        assertThat(productos.findById(vestido.getId()).orElseThrow().getStock()).isEqualTo(4);
        assertThat(productos.findById(collar.getId()).orElseThrow().getStock()).isZero();

        // El carrito quedó vacío tras la compra
        assertThat(carrito.countByUsuarioId(cliente1.getId())).isZero();

        // El detalle confirma el pedido y su factura asociada
        mvc.perform(get("/cliente/pedido/" + pedidoId).with(como(cliente1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pedido #" + pedidoId)));
    }

    @Test
    void compraAbortaSiNoHayStockYSinGuardarNada() throws Exception {
        // Fila de carrito con cantidad mayor al stock (escenario de carrera).
        CarritoItem trampa = new CarritoItem();
        trampa.setUsuario(cliente2);
        trampa.setProducto(collar); // stock 2
        trampa.setCantidad(50);
        carrito.save(trampa);

        mvc.perform(post("/cliente/checkout")
                        .param("direccion", "Donde sea")
                        .with(como(cliente2)))
                .andExpect(content().string(containsString("No hay stock suficiente")));

        assertThat(pedidos.findByUsuarioIdOrderByCreadoEnDesc(cliente2.getId())).isEmpty();
        assertThat(productos.findById(collar.getId()).orElseThrow().getStock()).isEqualTo(2);
    }

    @Test
    void compraExigeDireccion() throws Exception {
        mvc.perform(post("/cliente/carrito/agregar").param("productoId", vestido.getId().toString())
                .with(como(cliente1)));
        mvc.perform(post("/cliente/checkout").param("direccion", "   ").with(como(cliente1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("dirección de entrega")));
        assertThat(pedidos.findByUsuarioIdOrderByCreadoEnDesc(cliente1.getId())).isEmpty();
    }

    /* ── 5: gestión e historial de pedidos ────────────────────────────── */

    @Test
    void historialSoloMuestraLosPedidosDelUsuario() throws Exception {
        seedPedido(cliente1, vestido, 1);
        seedPedido(cliente2, collar, 1);

        String html = mvc.perform(get("/cliente/pedidos").with(como(cliente1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Vestido Aurora").doesNotContain("Collar Testigo");
    }

    @Test
    void pedidoOFacturaDeOtroUsuarioNoSePuedenAbrir() throws Exception {
        Pedido p = seedPedido(cliente1, vestido, 1);
        mvc.perform(get("/cliente/pedido/" + p.getId()).with(como(cliente2)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente"));
        mvc.perform(get("/cliente/factura/" + p.getId()).with(como(cliente2)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente"));
    }

    @Test
    void cancelarPedidoDevuelveElStock() throws Exception {
        Pedido p = seedPedido(cliente1, collar, 2); // collar stock 2 → queda 0
        assertThat(productos.findById(collar.getId()).orElseThrow().getStock()).isZero();

        mvc.perform(post("/cliente/pedido/" + p.getId() + "/cancelar").with(como(cliente1)))
                .andExpect(status().is3xxRedirection());

        assertThat(pedidos.findById(p.getId()).orElseThrow().getEstado()).isEqualTo(PedidoEstado.cancelado);
        assertThat(productos.findById(collar.getId()).orElseThrow().getStock()).isEqualTo(2);
    }

    @Test
    void noSePuedeCancelarUnPedidoEntregado() throws Exception {
        Pedido p = seedPedido(cliente1, vestido, 1);
        p.setEstado(PedidoEstado.entregado);
        pedidos.save(p);
        pedidos.flush();

        mvc.perform(post("/cliente/pedido/" + p.getId() + "/cancelar").with(como(cliente1)))
                .andExpect(redirectedUrl("/cliente"));
        assertThat(pedidos.findById(p.getId()).orElseThrow().getEstado()).isEqualTo(PedidoEstado.entregado);
    }

    /* ── 7: favoritos persistentes ────────────────────────────────────── */

    @Test
    void favoritosAlternaYPersiste() throws Exception {
        mvc.perform(post("/cliente/favoritos/toggle/ajax").param("productoId", vestido.getId().toString())
                        .with(como(cliente1)))
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.favorito").value(true));
        assertThat(favoritos.existsByUsuarioIdAndProductoId(cliente1.getId(), vestido.getId())).isTrue();

        mvc.perform(post("/cliente/favoritos/toggle/ajax").param("productoId", vestido.getId().toString())
                        .with(como(cliente1)))
                .andExpect(jsonPath("$.favorito").value(false));
        assertThat(favoritos.existsByUsuarioIdAndProductoId(cliente1.getId(), vestido.getId())).isFalse();

        // Guardado por formulario y visible en la página
        mvc.perform(post("/cliente/favoritos/toggle").param("productoId", vestido.getId().toString())
                .with(como(cliente1))).andExpect(status().is3xxRedirection());
        mvc.perform(get("/cliente/favoritos").with(como(cliente1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vestido Aurora")));

        // Corazón activo en el catálogo para ese usuario
        mvc.perform(get("/cliente").with(como(cliente1)))
                .andExpect(content().string(containsString("fav-btn active")));
    }

    /* ── 9: reseñas y calificaciones ──────────────────────────────────── */

    @Test
    void resenasSoloConPedidoEntregadoAlimentanEstrellasYSoloElDuenoEdita() throws Exception {
        // Sin pedido entregado: no puede reseñar
        mvc.perform(post("/cliente/resenas")
                        .param("productoId", vestido.getId().toString())
                        .param("calificacion", "5")
                        .param("comentario", "Hermoso")
                        .with(como(cliente1)))
                .andExpect(status().is3xxRedirection());
        assertThat(resenas.findByProductoIdOrderByCreadoEnDesc(vestido.getId())).isEmpty();

        // Compra entregada → publica
        Pedido p = seedPedido(cliente1, vestido, 1);
        p.setEstado(PedidoEstado.entregado);
        pedidos.save(p);
        pedidos.flush();

        mvc.perform(post("/cliente/resenas")
                        .param("productoId", vestido.getId().toString())
                        .param("calificacion", "4")
                        .param("comentario", "Muy buen vestido")
                        .with(como(cliente1)))
                .andExpect(redirectedUrl("/cliente/producto/" + vestido.getId()));
        assertThat(resenas.findByProductoIdOrderByCreadoEnDesc(vestido.getId())).hasSize(1);

        // Publicar de nuevo actualiza la propia (no duplica)
        mvc.perform(post("/cliente/resenas")
                        .param("productoId", vestido.getId().toString())
                        .param("calificacion", "5")
                        .param("comentario", "Al final me encantó")
                        .with(como(cliente1)))
                .andExpect(status().is3xxRedirection());
        assertThat(resenas.findByProductoIdOrderByCreadoEnDesc(vestido.getId())).hasSize(1);

        // Segundo comprador entregado deja la suya → dos reseñas
        Pedido p2 = seedPedido(cliente2, vestido, 1);
        p2.setEstado(PedidoEstado.entregado);
        pedidos.save(p2);
        pedidos.flush();
        mvc.perform(post("/cliente/resenas")
                .param("productoId", vestido.getId().toString())
                .param("calificacion", "1")
                .param("comentario", "No era mi talla")
                .with(como(cliente2)));
        assertThat(resenas.findByProductoIdOrderByCreadoEnDesc(vestido.getId())).hasSize(2);

        // La ficha muestra las reseñas
        mvc.perform(get("/cliente/producto/" + vestido.getId()).with(como(cliente2)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Al final me encant")))
                .andExpect(content().string(containsString("No era mi talla")));

        // El catálogo muestra estrellas agregadas
        mvc.perform(get("/cliente").with(como(cliente2)))
                .andExpect(content().string(containsString("prod-rating")));

        // Beto intenta borrar la reseña de Ana → rechazado; la suya sí puede
        Resena deAna = resenas.findByProductoIdOrderByCreadoEnDesc(vestido.getId()).stream()
                .filter(r -> r.getUsuario().getId().equals(cliente1.getId()))
                .findFirst().orElseThrow();
        mvc.perform(post("/cliente/resenas/" + deAna.getId() + "/eliminar")
                        .param("productoId", vestido.getId().toString())
                        .with(como(cliente2)))
                .andExpect(redirectedUrl("/cliente"));
        assertThat(resenas.existsById(deAna.getId())).isTrue();
    }

    /* ── 6: perfil de usuario ─────────────────────────────────────────── */

    @Test
    void perfilActualizaDatosYProtegeElCambioDeContrasena() throws Exception {
        mvc.perform(post("/cliente/perfil/datos")
                        .param("nombre", "Ana C. Nueva")
                        .param("telefono", "3134445566")
                        .param("ciudad", "Bogotá")
                        .param("direccion", "Calle 9 #76-43")
                        .with(como(cliente1)))
                .andExpect(status().is3xxRedirection());
        Usuario actualizado = usuarios.findById(cliente1.getId()).orElseThrow();
        assertThat(actualizado.getNombre()).isEqualTo("Ana C. Nueva");
        assertThat(actualizado.getCiudad()).isEqualTo("Bogotá");
        assertThat(actualizado.getDireccion()).isEqualTo("Calle 9 #76-43");

        // Cuenta Google (sin contraseña): puede crear una
        mvc.perform(post("/cliente/perfil/contrasena")
                        .param("nueva", "secreta1")
                        .param("confirmar", "secreta1")
                        .with(como(cliente1)))
                .andExpect(status().is3xxRedirection());
        String hash = usuarios.findById(cliente1.getId()).orElseThrow().getContrasena();
        assertThat(hash).isNotNull();

        // Cambio con contraseña actual incorrecta → no cambia
        mvc.perform(post("/cliente/perfil/contrasena")
                        .param("actual", "MALA")
                        .param("nueva", "otra12345")
                        .param("confirmar", "otra12345")
                        .with(como(cliente1)))
                .andExpect(status().is3xxRedirection());
        assertThat(usuarios.findById(cliente1.getId()).orElseThrow().getContrasena()).isEqualTo(hash);

        // Con la actual correcta → BCrypt verificado sobre el hash de la BD
        mvc.perform(post("/cliente/perfil/contrasena")
                        .param("actual", "secreta1")
                        .param("nueva", "nuevaClave1")
                        .param("confirmar", "nuevaClave1")
                        .with(como(cliente1)))
                .andExpect(status().is3xxRedirection());
        assertThat(usuarios.findById(cliente1.getId()).orElseThrow().getContrasena()).isNotEqualTo(hash);
    }

    @Test
    void loginRealFuncionaConLaContrasenaBCryptGuardada() throws Exception {
        perfilService.cambiarContrasena(cliente2.getId(), null, "secreta1", "secreta1");
        mvc.perform(post("/login").param("correo", "beto@test.co").param("contrasena", "secreta1")
                        .with(csrf()))
                .andExpect(authenticated())
                .andExpect(redirectedUrl("/cliente"));
    }

    /* ── 8: facturas ──────────────────────────────────────────────────── */

    @Test
    void facturaDelPedidoPropioSeGeneraNumerada() throws Exception {
        Pedido p = seedPedido(cliente1, vestido, 2);
        mvc.perform(get("/cliente/factura/" + p.getId()).with(como(cliente1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(String.format("FAC-%06d", p.getId()))))
                .andExpect(content().string(containsString("Vestido Aurora")))
                .andExpect(content().string(containsString("Ana Compra")));
    }

    /* ── Seguridad: rutas del módulo solo para rol CLIENTE ─────────────── */

    @Test
    void rutasDeClienteBloqueadasParaOtrosRolesYAnonimos() throws Exception {
        mvc.perform(get("/cliente").with(como(emprendedor))).andExpect(status().isForbidden());
        mvc.perform(post("/cliente/carrito/agregar").param("productoId", vestido.getId().toString())
                .with(como(emprendedor))).andExpect(status().isForbidden());
        mvc.perform(get("/cliente/perfil").with(como(emprendedor))).andExpect(status().isForbidden());
        mvc.perform(get("/cliente")).andExpect(status().is3xxRedirection()); // anónimo → login
    }

    @Test
    void csrfEsObligatorioYElCatalogoEntregaTokenParaAjax() throws Exception {
        mvc.perform(get("/cliente").with(como(cliente1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("name=\"_csrf_header\"")));

        // Autenticado, pero sin token: Spring Security debe rechazar el POST.
        mvc.perform(post("/cliente/favoritos/toggle/ajax")
                        .param("productoId", vestido.getId().toString())
                        .with(autenticadoSinCsrf(cliente1)))
                .andExpect(status().isForbidden());

        // El mismo POST con token válido sí se procesa y persiste.
        mvc.perform(post("/cliente/favoritos/toggle/ajax")
                        .param("productoId", vestido.getId().toString())
                        .with(como(cliente1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorito").value(true));
        assertThat(favoritos.existsByUsuarioIdAndProductoId(cliente1.getId(), vestido.getId())).isTrue();
    }

    @Test
    void favoritosYPerfilSoloAfectanAlClienteAutenticado() throws Exception {
        mvc.perform(post("/cliente/favoritos/toggle")
                        .param("productoId", vestido.getId().toString())
                        .with(como(cliente1)))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/cliente/favoritos").with(como(cliente2)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Vestido Aurora"))));

        mvc.perform(post("/cliente/perfil/datos")
                        .param("nombre", "Beto Modificado")
                        .param("telefono", "3137778899")
                        .with(como(cliente2)))
                .andExpect(status().is3xxRedirection());

        assertThat(usuarios.findById(cliente1.getId()).orElseThrow().getNombre()).isEqualTo("Ana Compra");
        assertThat(usuarios.findById(cliente2.getId()).orElseThrow().getNombre()).isEqualTo("Beto Modificado");
        assertThat(favoritos.existsByUsuarioIdAndProductoId(cliente1.getId(), vestido.getId())).isTrue();
        assertThat(favoritos.existsByUsuarioIdAndProductoId(cliente2.getId(), vestido.getId())).isFalse();
    }

    @Test
    void filtrosEnTiendaPublicaProductosRelacionadosYVolverAComprarPedido() throws Exception {
        // 1) Tienda pública filtra por búsqueda y por oferta
        mvc.perform(get("/tienda/" + tienda.getId()).param("q", "Aurora").with(como(cliente1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vestido Aurora")))
                .andExpect(content().string(not(containsString("Collar Testigo"))));

        mvc.perform(get("/tienda/" + tienda.getId()).param("oferta", "true").with(como(cliente1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vestido Aurora")))
                .andExpect(content().string(not(containsString("Collar Testigo"))));

        // 2) Ficha de producto muestra productos relacionados de la misma tienda/categoría
        mvc.perform(get("/cliente/producto/" + vestido.getId()).with(como(cliente1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Productos relacionados")))
                .andExpect(content().string(containsString("Collar Testigo")));

        // 3) Historial de pedidos permite filtrar por estado y "Volver a comprar" (reordenar)
        Pedido pedido = seedPedido(cliente1, vestido, 1);

        mvc.perform(get("/cliente/pedidos").param("estado", "pendiente").with(como(cliente1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pedido #" + pedido.getId())));

        mvc.perform(get("/cliente/pedidos").param("estado", "entregado").with(como(cliente1)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Pedido #" + pedido.getId()))));

        assertThat(carrito.countByUsuarioId(cliente1.getId())).isZero();

        mvc.perform(post("/cliente/pedido/" + pedido.getId() + "/reordenar").with(como(cliente1)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente/carrito"));

        assertThat(carrito.countByUsuarioId(cliente1.getId())).isEqualTo(1);
    }

    /* ── utilidades ───────────────────────────────────────────────────── */

    private static RequestPostProcessor como(Usuario u) {
        RequestPostProcessor auth = autenticadoSinCsrf(u);
        return request -> csrf().postProcessRequest(auth.postProcessRequest(request));
    }

    private static RequestPostProcessor autenticadoSinCsrf(Usuario u) {
        UsuarioPrincipal up = new UsuarioPrincipal(u);
        return authentication(new UsernamePasswordAuthenticationToken(up, null, up.getAuthorities()));
    }

    private Usuario usuario(String nombre, String correo) {
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setCorreo(correo);
        u.setRol(Rol.cliente);
        u.setActivo(true);
        return usuarios.save(u);
    }

    private Producto producto(String nombre, double precio, Double oferta, int stock,
                              Integer categoriaId, Tienda t) {
        Producto p = new Producto();
        p.setNombre(nombre);
        p.setDescripcion("Descripción de prueba de " + nombre);
        p.setPrecio(java.math.BigDecimal.valueOf(precio));
        p.setPrecioOferta(oferta == null ? null : java.math.BigDecimal.valueOf(oferta));
        p.setStock(stock);
        p.setCategoriaId(categoriaId);
        p.setActivo(true);
        p.setEstadoVerificacion(EstadoVerificacion.aprobado);
        p.setTienda(t);
        return productos.save(p);
    }

    /** Crea un pedido usando el MISMO servicio que usa el checkout web (compra real). */
    private Pedido seedPedido(Usuario comprador, Producto p, int cantidad) {
        carritoService.agregar(comprador.getId(), p.getId(), cantidad);
        return checkoutService.comprar(comprador.getId(), "Dirección de prueba 123", "3130000000", null);
    }
}
