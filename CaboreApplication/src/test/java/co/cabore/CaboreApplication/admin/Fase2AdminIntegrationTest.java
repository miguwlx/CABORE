package co.cabore.CaboreApplication.admin;

import co.cabore.CaboreApplication.config.UsuarioPrincipal;
import co.cabore.CaboreApplication.model.*;
import co.cabore.CaboreApplication.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FASE 2 — Módulo Administrador (Diana).
 * Pruebas integrales de extremo a extremo: permisos, dashboard, verificación de
 * productos, usuarios, categorías, tiendas, facturas, reportes y exportación CSV,
 * además de su impacto real sobre el catálogo del cliente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Fase2AdminIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UsuarioRepository usuarios;
    @Autowired TiendaRepository tiendas;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    @Autowired PedidoRepository pedidos;

    Usuario admin, otroAdmin, cliente, emprendedor;
    Tienda tienda;
    Categoria catModa, catVacia;
    Producto prodPendiente, prodAprobado;
    Pedido pedido;

    @BeforeEach
    void seed() {
        admin = crearUsuario("Diana Admin", "diana.admin@cabore.co", Rol.administrador);
        otroAdmin = crearUsuario("Miguel Admin", "miguel.admin@cabore.co", Rol.administrador);
        cliente = crearUsuario("Carlos Cliente", "carlos.cli@cabore.co", Rol.cliente);
        emprendedor = crearUsuario("María Emprendedora", "maria.emp@cabore.co", Rol.emprendedor);

        catModa = new Categoria();
        catModa.setNombre("Ropa y Moda");
        catModa.setIcono("👗");
        catModa = categorias.save(catModa);

        catVacia = new Categoria();
        catVacia.setNombre("Categoría Vacía");
        catVacia.setIcono("📦");
        catVacia = categorias.save(catVacia);

        tienda = new Tienda();
        tienda.setUsuario(emprendedor);
        tienda.setNombre("Boutique María");
        tienda.setDescripcion("Ropa artesanal");
        tienda.setActivo(true);
        tienda = tiendas.save(tienda);

        prodPendiente = new Producto();
        prodPendiente.setNombre("Ruana de Lana");
        prodPendiente.setDescripcion("Ruana tejida a mano");
        prodPendiente.setPrecio(new BigDecimal("150000.00"));
        prodPendiente.setStock(5);
        prodPendiente.setCategoriaId(catModa.getId());
        prodPendiente.setTienda(tienda);
        prodPendiente.setEstadoVerificacion(EstadoVerificacion.pendiente);
        prodPendiente = productos.save(prodPendiente);

        prodAprobado = new Producto();
        prodAprobado.setNombre("Sombrero Vueltiao");
        prodAprobado.setDescripcion("Sombrero original");
        prodAprobado.setPrecio(new BigDecimal("120000.00"));
        prodAprobado.setStock(8);
        prodAprobado.setCategoriaId(catModa.getId());
        prodAprobado.setTienda(tienda);
        prodAprobado.setEstadoVerificacion(EstadoVerificacion.aprobado);
        prodAprobado = productos.save(prodAprobado);

        pedido = new Pedido();
        pedido.setUsuario(cliente);
        pedido.setEstado(PedidoEstado.confirmado);
        pedido.setDireccion("Cra 10 #20-30");
        pedido.setTotal(new BigDecimal("240000.00"));
        PedidoItem item = new PedidoItem();
        item.setProducto(prodAprobado);
        item.setCantidad(2);
        item.setPrecioUnit(new BigDecimal("120000.00"));
        item.setPrecioUnitario(new BigDecimal("120000.00"));
        item.setSubtotal(new BigDecimal("240000.00"));
        pedido.addItem(item);
        pedido = pedidos.save(pedido);
    }

    /* ── 1. Login y permisos de administrador ──────────────────────────── */

    @Test
    void loginDeAdministradorRedirigeAPanelAdminYBloqueaOtrosRoles() throws Exception {
        mvc.perform(post("/login")
                        .param("correo", "diana.admin@cabore.co")
                        .param("contrasena", "admin123")
                        .with(csrf()))
                .andExpect(authenticated().withRoles("ADMINISTRADOR"))
                .andExpect(redirectedUrl("/admin"));

        // Cliente y emprendedor no pueden entrar a /admin ni a sus endpoints
        mvc.perform(get("/admin").with(como(cliente))).andExpect(status().isForbidden());
        mvc.perform(get("/admin").with(como(emprendedor))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/usuarios/estado/ajax")
                        .param("usuario_id", cliente.getId().toString())
                        .param("activo", "0")
                        .with(como(cliente)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
    }

    /* ── 2. Dashboard / Resumen ────────────────────────────────────────── */

    @Test
    void dashboardMuestraEstadisticasGeneralesYPreviews() throws Exception {
        mvc.perform(get("/admin").with(como(admin)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"))
                .andExpect(content().string(containsString("Diana Admin")))
                .andExpect(content().string(containsString("1 clientes · 1 emprendedores")))
                .andExpect(content().string(containsString("de 2 productos totales")))
                .andExpect(content().string(containsString("$240.000")))
                .andExpect(content().string(containsString("Ruana de Lana")))
                .andExpect(content().string(containsString("Carlos Cliente")));
    }

    /* ── 3. Verificación de productos (aprobar / rechazar) ─────────────── */

    @Test
    void aprobarYRechazarProductosActualizaEstadoEImpactaCatalogoCliente() throws Exception {
        // Antes de aprobar, el producto pendiente NO aparece en el catálogo del cliente
        mvc.perform(get("/cliente").with(como(cliente)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Ruana de Lana"))));

        // Rechazar sin motivo falla
        mvc.perform(post("/admin/productos/verificar/ajax")
                        .param("producto_id", prodPendiente.getId().toString())
                        .param("accion", "rechazar")
                        .param("motivo", "   ")
                        .with(como(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error").value("Debes indicar un motivo de rechazo."));

        // Aprobar producto pendiente
        mvc.perform(post("/admin/productos/verificar/ajax")
                        .param("producto_id", prodPendiente.getId().toString())
                        .param("accion", "aprobar")
                        .with(como(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.estado").value("aprobado"));

        Producto aprobado = productos.findById(prodPendiente.getId()).orElseThrow();
        assertThat(aprobado.getEstadoVerificacion()).isEqualTo(EstadoVerificacion.aprobado);
        assertThat(aprobado.getRevisadoPor()).isEqualTo(admin.getId());
        assertThat(aprobado.getRevisadoEn()).isNotNull();

        // Ahora SÍ aparece en el catálogo del cliente
        mvc.perform(get("/cliente").with(como(cliente)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ruana de Lana")));

        // Rechazar con motivo
        mvc.perform(post("/admin/productos/verificar/ajax")
                        .param("producto_id", prodPendiente.getId().toString())
                        .param("accion", "rechazar")
                        .param("motivo", "Imagen borrosa")
                        .with(como(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.estado").value("rechazado"));

        Producto rechazado = productos.findById(prodPendiente.getId()).orElseThrow();
        assertThat(rechazado.getEstadoVerificacion()).isEqualTo(EstadoVerificacion.rechazado);
        assertThat(rechazado.getMotivoRechazo()).isEqualTo("Imagen borrosa");

        // Aparece en la pestaña de rechazados con su motivo
        mvc.perform(get("/admin").param("tab", "verificacion").param("vtab", "rechazado").with(como(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ruana de Lana")))
                .andExpect(content().string(containsString("Imagen borrosa")));
    }

    /* ── 4. Gestión de usuarios ────────────────────────────────────────── */

    @Test
    void gestionDeUsuariosFiltraSuspendeYProtegeAdministradores() throws Exception {
        // Filtro por rol y búsqueda
        mvc.perform(get("/admin").param("tab", "usuarios").param("rol", "cliente").param("u_buscar", "Carlos")
                        .with(como(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"user-" + cliente.getId() + "\"")))
                .andExpect(content().string(not(containsString("id=\"user-" + emprendedor.getId() + "\""))))
                .andExpect(content().string(not(containsString("diana.admin@cabore.co"))));

        // No puede suspenderse a sí mismo
        mvc.perform(post("/admin/usuarios/estado/ajax")
                        .param("usuario_id", admin.getId().toString())
                        .param("activo", "0")
                        .with(como(admin)))
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error").value("No puedes suspender tu propia cuenta."));

        // No puede suspender a otro administrador
        mvc.perform(post("/admin/usuarios/estado/ajax")
                        .param("usuario_id", otroAdmin.getId().toString())
                        .param("activo", "0")
                        .with(como(admin)))
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error").value("No puedes suspender a otro administrador."));

        // Suspender cliente
        mvc.perform(post("/admin/usuarios/estado/ajax")
                        .param("usuario_id", cliente.getId().toString())
                        .param("activo", "0")
                        .with(como(admin)))
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.activo").value(0));
        assertThat(usuarios.findById(cliente.getId()).orElseThrow().isActivo()).isFalse();

        // Cliente suspendido no puede iniciar sesión
        mvc.perform(post("/login")
                        .param("correo", "carlos.cli@cabore.co")
                        .param("contrasena", "admin123")
                        .with(csrf()))
                .andExpect(redirectedUrl("/login?error=suspendida"));

        // Reactivar cliente
        mvc.perform(post("/admin/usuarios/estado/ajax")
                        .param("usuario_id", cliente.getId().toString())
                        .param("activo", "1")
                        .with(como(admin)))
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.activo").value(1));
        assertThat(usuarios.findById(cliente.getId()).orElseThrow().isActivo()).isTrue();
    }

    /* ── 5. Gestión de categorías ──────────────────────────────────────── */

    @Test
    void gestionDeCategoriasCreaEditaYValidaEliminacion() throws Exception {
        // Crear categoría
        mvc.perform(post("/admin/categorias/ajax")
                        .param("accion", "guardar")
                        .param("nombre", "Joyería Artesanal")
                        .param("icono", "💍")
                        .with(como(admin)))
                .andExpect(jsonPath("$.ok").value(true));

        Categoria creada = categorias.findAllByOrderByNombreAsc().stream()
                .filter(c -> c.getNombre().equals("Joyería Artesanal"))
                .findFirst().orElseThrow();
        assertThat(creada.getIconoSeguro()).isEqualTo("💍");

        // Editar categoría
        mvc.perform(post("/admin/categorias/ajax")
                        .param("accion", "guardar")
                        .param("id", creada.getId().toString())
                        .param("nombre", "Joyería Fina")
                        .param("icono", "")
                        .with(como(admin)))
                .andExpect(jsonPath("$.ok").value(true));
        assertThat(categorias.findById(creada.getId()).orElseThrow().getNombre()).isEqualTo("Joyería Fina");
        assertThat(categorias.findById(creada.getId()).orElseThrow().getIconoSeguro()).isEqualTo("🏷️");

        // No permite eliminar categoría con productos asignados
        mvc.perform(post("/admin/categorias/ajax")
                        .param("accion", "eliminar")
                        .param("id", catModa.getId().toString())
                        .with(como(admin)))
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error").value("No puedes eliminar una categoría con productos asignados."));
        assertThat(categorias.existsById(catModa.getId())).isTrue();

        // Permite eliminar categoría sin productos
        mvc.perform(post("/admin/categorias/ajax")
                        .param("accion", "eliminar")
                        .param("id", catVacia.getId().toString())
                        .with(como(admin)))
                .andExpect(jsonPath("$.ok").value(true));
        assertThat(categorias.existsById(catVacia.getId())).isFalse();
    }

    /* ── 6. Gestión de tiendas ─────────────────────────────────────────── */

    @Test
    void gestionDeTiendasSuspendeConMotivoSincronizaColumnasYReactiva() throws Exception {
        // Suspender sin motivo falla
        mvc.perform(post("/admin/tiendas/estado/ajax")
                        .param("tienda_id", tienda.getId().toString())
                        .param("activo", "0")
                        .param("motivo", "")
                        .with(como(admin)))
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error").value("Debes indicar un motivo de suspensión."));

        // Suspender con motivo
        mvc.perform(post("/admin/tiendas/estado/ajax")
                        .param("tienda_id", tienda.getId().toString())
                        .param("activo", "0")
                        .param("motivo", "Incumplimiento de entregas")
                        .with(como(admin)))
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.activo").value(0));

        Tienda suspendida = tiendas.findById(tienda.getId()).orElseThrow();
        assertThat(suspendida.isActivo()).isFalse();
        assertThat(suspendida.isActiva()).isFalse();
        assertThat(suspendida.getMotivoSuspension()).isEqualTo("Incumplimiento de entregas");

        // Sus productos dejan de aparecer en el catálogo del cliente
        mvc.perform(get("/cliente").with(como(cliente)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Sombrero Vueltiao"))));

        // Reactivar tienda
        mvc.perform(post("/admin/tiendas/estado/ajax")
                        .param("tienda_id", tienda.getId().toString())
                        .param("activo", "1")
                        .with(como(admin)))
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.activo").value(1));

        Tienda reactivada = tiendas.findById(tienda.getId()).orElseThrow();
        assertThat(reactivada.isActivo()).isTrue();
        assertThat(reactivada.isActiva()).isTrue();
        assertThat(reactivada.getMotivoSuspension()).isNull();
    }

    /* ── 7. Reportes, facturas y exportación CSV ───────────────────────── */

    @Test
    void reportesFacturasYExportacionCsvFuncionanConFiltros() throws Exception {
        String hoy = LocalDate.now().toString();

        // Reporte general y por tienda
        mvc.perform(get("/admin")
                        .param("tab", "reportes")
                        .param("desde", LocalDate.now().minusDays(7).toString())
                        .param("hasta", hoy)
                        .param("estado", "confirmado")
                        .with(como(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Boutique María")))
                .andExpect(content().string(containsString("Sombrero Vueltiao")))
                .andExpect(content().string(containsString("$240.000")));

        // Exportación CSV con BOM UTF-8
        byte[] csvBytes = mvc.perform(get("/admin/reportes/csv")
                        .param("desde", LocalDate.now().minusDays(7).toString())
                        .param("hasta", hoy)
                        .param("estado", "confirmado")
                        .param("tienda_id", tienda.getId().toString())
                        .with(como(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("reporte_ventas_")))
                .andReturn().getResponse().getContentAsByteArray();

        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFPedido,Fecha,Cliente,Correo,Tienda(s),Estado,Total,Dirección");
        assertThat(csv).contains(pedido.getNumeroFactura());
        assertThat(csv).contains("Carlos Cliente");
        assertThat(csv).contains("Boutique María");
        assertThat(csv).contains("240000.00");

        // Factura imprimible desde el administrador
        mvc.perform(get("/admin/factura/" + pedido.getId()).with(como(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(pedido.getNumeroFactura())))
                .andExpect(content().string(containsString("Sombrero Vueltiao")))
                .andExpect(content().string(containsString("Volver a facturas")));

        // Filtro de facturas por estado y actualización de estado de pedido por el administrador
        mvc.perform(get("/admin").param("tab", "facturas").param("f_estado", "confirmado").with(como(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(pedido.getNumeroFactura())));

        int stockAntes = productos.findById(prodAprobado.getId()).orElseThrow().getStock();
        mvc.perform(post("/admin/pedidos/estado/ajax")
                        .param("pedido_id", pedido.getId().toString())
                        .param("estado", "cancelado")
                        .with(como(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.estado").value("cancelado"));

        assertThat(pedidos.findById(pedido.getId()).orElseThrow().getEstado()).isEqualTo(PedidoEstado.cancelado);
        assertThat(productos.findById(prodAprobado.getId()).orElseThrow().getStock()).isEqualTo(stockAntes + 2);
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
        u.setContrasena(passwordEncoder.encode("admin123"));
        u.setRol(rol);
        u.setActivo(true);
        return usuarios.save(u);
    }
}
