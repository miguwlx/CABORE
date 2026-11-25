package co.cabore.CaboreApplication.estabilizacion;

import co.cabore.CaboreApplication.config.UsuarioPrincipal;
import co.cabore.CaboreApplication.model.*;
import co.cabore.CaboreApplication.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Estabilización: producto nuevo pendiente, pedidos antiguos con precio en cero y
 * eliminación de productos sin perder el historial. Corre sobre H2 (modo MariaDB).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EstabilizacionIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired TiendaRepository tiendas;
    @Autowired ProductoRepository productos;
    @Autowired PedidoRepository pedidos;
    @Autowired PedidoItemRepository pedidoItems;
    @Autowired CarritoItemRepository carrito;

    Usuario emprendedor, cliente;
    Tienda tienda;

    @BeforeEach
    void seed() {
        emprendedor = usuario("Eva Emprendedora", "eva@test.co", Rol.emprendedor);
        cliente = usuario("Carlos Cliente", "carlos@test.co", Rol.cliente);
        tienda = new Tienda();
        tienda.setUsuario(emprendedor);
        tienda.setNombre("Tienda Estabilización");
        tienda = tiendas.save(tienda);
    }

    @Test
    void productoCreadoPorEmprendedorQuedaPendienteYNoApareceEnCatalogo() throws Exception {
        mvc.perform(post("/emprendedor/productos")
                        .param("nombre", "Mochila Wayuu Única")
                        .param("precio", "45000.50")
                        .param("stock", "3")
                        .with(como(emprendedor)))
                .andExpect(status().is3xxRedirection());

        var lista = productos.findByTiendaId(tienda.getId());
        assertThat(lista).hasSize(1);
        Producto p = lista.get(0);
        assertThat(p.getEstadoVerificacion()).isEqualTo(EstadoVerificacion.pendiente);
        assertThat(p.getCategoriaId()).isEqualTo(12);
        assertThat(p.getPrecio()).isEqualByComparingTo("45000.50");

        mvc.perform(get("/cliente").with(como(cliente)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Mochila Wayuu Única"))));
    }

    @Test
    void facturaYDetalleDePedidoAntiguoMuestranElPrecioDeRespaldo() throws Exception {
        Producto vestido = producto("Vestido floral", "89000.00");
        Pedido pedido = pedidoAntiguo(vestido, 1, "89000.00");

        mvc.perform(get("/cliente/factura/" + pedido.getId()).with(como(cliente)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("$89.000")));
        mvc.perform(get("/cliente/pedido/" + pedido.getId()).with(como(cliente)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("$89.000")));
    }

    @Test
    void eliminarProductoConPedidosLoArchivaYConservaElHistorial() throws Exception {
        Producto vendido = producto("Collar vendido", "45000.00");
        Pedido pedido = pedidoAntiguo(vendido, 1, "45000.00");
        CarritoItem enCarrito = new CarritoItem();
        enCarrito.setUsuario(cliente);
        enCarrito.setProducto(vendido);
        carrito.save(enCarrito);

        mvc.perform(post("/emprendedor/productos/" + vendido.getId() + "/eliminar").with(como(emprendedor)))
                .andExpect(status().is3xxRedirection());

        Producto despues = productos.findById(vendido.getId()).orElseThrow(); // sigue existiendo
        assertThat(despues.isActivo()).isFalse();
        assertThat(pedidoItems.existsByProductoId(vendido.getId())).isTrue();
        assertThat(pedidos.findById(pedido.getId()).orElseThrow().getItems()).hasSize(1);
        assertThat(carrito.countByUsuarioId(cliente.getId())).isZero();
    }

    @Test
    void eliminarProductoSinPedidosLoBorra() throws Exception {
        Producto nuevo = producto("Producto sin ventas", "10000.00");
        Long id = nuevo.getId();

        mvc.perform(post("/emprendedor/productos/" + id + "/eliminar").with(como(emprendedor)))
                .andExpect(status().is3xxRedirection());

        assertThat(productos.findById(id)).isEmpty();
    }

    @Test
    void emprendedorNoPuedeEliminarProductosDeOtraTienda() throws Exception {
        Usuario otro = usuario("Otro Emprendedor", "otro@test.co", Rol.emprendedor);
        Tienda otraTienda = new Tienda();
        otraTienda.setUsuario(otro);
        otraTienda = tiendas.save(otraTienda);
        Producto ajeno = producto("Producto ajeno", "5000.00");
        ajeno.setTienda(otraTienda);
        productos.save(ajeno);

        mvc.perform(post("/emprendedor/productos/" + ajeno.getId() + "/eliminar").with(como(emprendedor)))
                .andExpect(status().isForbidden());
        assertThat(productos.findById(ajeno.getId())).isPresent();
    }

    /* ── utilidades ───────────────────────────────────────────────────── */

    private static RequestPostProcessor como(Usuario u) {
        UsuarioPrincipal up = new UsuarioPrincipal(u);
        RequestPostProcessor auth = authentication(
                new UsernamePasswordAuthenticationToken(up, null, up.getAuthorities()));
        return request -> csrf().postProcessRequest(auth.postProcessRequest(request));
    }

    private Usuario usuario(String nombre, String correo, Rol rol) {
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setCorreo(correo);
        u.setRol(rol);
        u.setActivo(true);
        return usuarios.save(u);
    }

    private Producto producto(String nombre, String precio) {
        Producto p = new Producto();
        p.setNombre(nombre);
        p.setPrecio(new BigDecimal(precio));
        p.setStock(5);
        p.setActivo(true);
        p.setEstadoVerificacion(EstadoVerificacion.aprobado);
        p.setTienda(tienda);
        return productos.save(p);
    }

    /** Pedido con el formato del dump antiguo: precio_unit = 0, subtotal = 0, precio real en precio_unitario. */
    private Pedido pedidoAntiguo(Producto p, int cantidad, String precioUnitario) {
        Pedido pedido = new Pedido();
        pedido.setUsuario(cliente);
        pedido.setDireccion("Calle 1 #2-3");
        pedido.setTotal(new BigDecimal(precioUnitario).multiply(BigDecimal.valueOf(cantidad)));
        PedidoItem item = new PedidoItem();
        item.setProducto(p);
        item.setCantidad(cantidad);
        item.setPrecioUnit(BigDecimal.ZERO);
        item.setSubtotal(BigDecimal.ZERO);
        item.setPrecioUnitario(new BigDecimal(precioUnitario));
        pedido.addItem(item);
        return pedidos.save(pedido);
    }
}
