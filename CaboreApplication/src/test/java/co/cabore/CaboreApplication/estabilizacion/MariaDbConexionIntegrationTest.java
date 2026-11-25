package co.cabore.CaboreApplication.estabilizacion;

import co.cabore.CaboreApplication.config.UsuarioPrincipal;
import co.cabore.CaboreApplication.model.*;
import co.cabore.CaboreApplication.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verificación de integración real contra MariaDB en localhost:3306 (perfil dev,
 * base de datos cabore_restaurada).
 *
 * Todas las pruebas usan @Transactional para que cualquier escritura se revierta
 * al terminar cada test y no altere los datos reales restaurados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@EnabledIf("mariadbDisponible")
class MariaDbConexionIntegrationTest {

    static boolean mariadbDisponible() {
        try (Connection c = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3306/cabore_restaurada", "root", "")) {
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    @Autowired DataSource dataSource;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UsuarioRepository usuarios;
    @Autowired TiendaRepository tiendas;
    @Autowired CategoriaRepository categorias;
    @Autowired ProductoRepository productos;
    @Autowired PedidoRepository pedidos;
    @Autowired PedidoItemRepository pedidoItems;
    @Autowired CarritoItemRepository carrito;
    @Autowired FavoritoRepository favoritos;
    @Autowired ResenaRepository resenas;

    @Test
    void conexionRealAMariaDbEnPuerto3306YEsquemaCompleto() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertThat(conn.getMetaData().getURL())
                    .contains("jdbc:mariadb://localhost")
                    .contains("cabore_restaurada");
            assertThat(conn.getMetaData().getDatabaseProductName()).containsIgnoringCase("MariaDB");
        }

        // Verificar lectura real de todas las entidades sobre MariaDB (al menos los registros base restaurados)
        assertThat(usuarios.count()).isGreaterThanOrEqualTo(14);
        assertThat(tiendas.count()).isGreaterThanOrEqualTo(6);
        assertThat(categorias.count()).isGreaterThanOrEqualTo(12);
        assertThat(productos.count()).isGreaterThanOrEqualTo(4);
        assertThat(pedidos.count()).isGreaterThanOrEqualTo(2);
        assertThat(pedidoItems.count()).isGreaterThanOrEqualTo(4);
        assertThat(favoritos.count()).isGreaterThanOrEqualTo(0);
        assertThat(resenas.count()).isGreaterThanOrEqualTo(0);
        assertThat(carrito.count()).isGreaterThanOrEqualTo(0);

        Long totalConversaciones = em.createQuery("select count(c) from Conversacion c", Long.class).getSingleResult();
        Long totalMensajes = em.createQuery("select count(m) from Mensaje m", Long.class).getSingleResult();
        assertThat(totalConversaciones).isGreaterThanOrEqualTo(1L);
        assertThat(totalMensajes).isGreaterThanOrEqualTo(3L);
    }

    @Test
    void loginRealContraMariaDbConHashesBcryptDePhp() throws Exception {
        // Verifica compatibilidad con hashes $2y$ de PHP password_hash() y redirección por rol
        String hashPhp2y = passwordEncoder.encode("ClavePrueba123").replaceFirst("^\\$2[ab]\\$", "\\$2y\\$");

        Usuario cliente = usuarios.findByCorreo("cliente@cabore.co").orElseThrow();
        cliente.setContrasena(hashPhp2y);
        usuarios.saveAndFlush(cliente);

        Usuario emp = usuarios.findByCorreo("emprendedor@cabore.co").orElseThrow();
        emp.setContrasena(hashPhp2y);
        usuarios.saveAndFlush(emp);

        Usuario suspendido = usuarios.findByCorreo("marianacal@gmail.com").orElseThrow();
        suspendido.setContrasena(hashPhp2y);
        usuarios.saveAndFlush(suspendido);

        mvc.perform(post("/login")
                        .param("correo", "cliente@cabore.co")
                        .param("contrasena", "ClavePrueba123")
                        .with(csrf()))
                .andExpect(authenticated().withRoles("CLIENTE"))
                .andExpect(redirectedUrl("/cliente"));

        mvc.perform(post("/login")
                        .param("correo", "emprendedor@cabore.co")
                        .param("contrasena", "ClavePrueba123")
                        .with(csrf()))
                .andExpect(authenticated().withRoles("EMPRENDEDOR"))
                .andExpect(redirectedUrl("/emprendedor"));

        // Cuenta con activo = 0 en MariaDB → redirige a error=suspendida
        mvc.perform(post("/login")
                        .param("correo", "marianacal@gmail.com")
                        .param("contrasena", "ClavePrueba123")
                        .with(csrf()))
                .andExpect(redirectedUrl("/login?error=suspendida"));
    }

    @Test
    void consultasPrincipalesYFacturaDePedidoAntiguoEnMariaDb() throws Exception {
        Usuario dianaCliente = usuarios.findById(7).orElseThrow();

        // Catálogo principal desde MariaDB
        mvc.perform(get("/cliente").with(como(dianaCliente)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vestido floral primavera")))
                .andExpect(content().string(containsString("Collar dorado minimalista")))
                .andExpect(content().string(containsString("Kit facial hidratante")))
                .andExpect(content().string(containsString("Blusa lino casual")))
                .andExpect(content().string(containsString("Boutique María")));

        // Pedido antiguo #3 en MariaDB (precio_unit = 0.00, precio_unitario = 45000 / 89000 / 120000)
        mvc.perform(get("/cliente/factura/3").with(como(dianaCliente)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("FAC-000003")))
                .andExpect(content().string(containsString("$45.000")))
                .andExpect(content().string(containsString("$89.000")))
                .andExpect(content().string(containsString("$120.000")))
                .andExpect(content().string(containsString("$254.000")));
    }

    @Test
    void lecturaYEscrituraTransaccionalEnMariaDbConRollback() throws Exception {
        Usuario carlos = usuarios.findByCorreo("cliente@cabore.co").orElseThrow();
        carrito.deleteByUsuarioId(carlos.getId());
        Producto vestido = productos.findById(1L).orElseThrow();
        int stockInicial = vestido.getStock();
        long pedidosIniciales = pedidos.count();

        // Escritura en carrito_items
        mvc.perform(post("/cliente/carrito/agregar")
                        .param("productoId", "1")
                        .param("cantidad", "2")
                        .with(como(carlos)))
                .andExpect(status().is3xxRedirection());

        assertThat(carrito.countByUsuarioId(carlos.getId())).isEqualTo(1);

        // Escritura en pedidos + pedido_items + descuento de stock en productos
        mvc.perform(post("/cliente/checkout")
                        .param("direccion", "Calle Real 100 #20-30")
                        .param("telefono", "3001234567")
                        .with(como(carlos)))
                .andExpect(status().is3xxRedirection());

        assertThat(carrito.countByUsuarioId(carlos.getId())).isZero();
        assertThat(productos.findById(1L).orElseThrow().getStock()).isEqualTo(stockInicial - 2);
        assertThat(pedidos.count()).isEqualTo(pedidosIniciales + 1);
    }

    @Test
    void moduloAdministradorIntegradoConMariaDbReal() throws Exception {
        Usuario admin = usuarios.findByCorreo("dianareyec03@gmail.com").orElseThrow();

        // Dashboard administrativo con datos reales de cabore_restaurada
        mvc.perform(get("/admin").with(como(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Diana Reyes")))
                .andExpect(content().string(containsString("Boutique María")))
                .andExpect(content().string(containsString("$254.000")))
                .andExpect(content().string(containsString("$89.000")));

        // Factura #3 vista como administrador
        mvc.perform(get("/admin/factura/3").with(como(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("FAC-000003")))
                .andExpect(content().string(containsString("$254.000")));

        // Exportación CSV real desde MariaDB
        String csv = mvc.perform(get("/admin/reportes/csv").with(como(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("FAC-000003").contains("FAC-000004").contains("Boutique María");
    }

    @Test
    void moduloEmprendedorYTiendaPublicaIntegradosConMariaDbReal() throws Exception {
        Usuario maria = usuarios.findByCorreo("emprendedor@cabore.co").orElseThrow();

        // Panel del emprendedor conectado a cabore_restaurada (tienda 1 "Boutique María",
        // plantilla "elegante", pedidos históricos de la tienda)
        mvc.perform(get("/emprendedor").with(como(maria)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Boutique María")))
                .andExpect(content().string(containsString("Producto estrella")))
                .andExpect(content().string(containsString("Vestido floral primavera")))
                .andExpect(content().string(containsString("$254.000")));

        // Tienda pública #1 accesible sin iniciar sesión con su plantilla "elegante" de MariaDB
        mvc.perform(get("/tienda/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("plantilla-elegante")))
                .andExpect(content().string(containsString("Boutique María")))
                .andExpect(content().string(containsString("Vestido floral primavera")))
                .andExpect(content().string(containsString("Collar dorado minimalista")));

        // Constructor Avanzado de Tiendas Personalizables conectado a MariaDB real (se revierte por @Transactional)
        mvc.perform(get("/emprendedor/constructor").with(como(maria)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Studio")))
                .andExpect(content().string(containsString("Boutique María")));

        String configPublicar = """
                {
                  "nombre": "Boutique María",
                  "eslogan": "Moda de autor en MariaDB",
                  "descripcion": "Tienda personalizada en MariaDB",
                  "ciudad": "Bogotá",
                  "plantilla": "elegante",
                  "colorPrimario": "#b8923c",
                  "portadaTitulo": "Colección Primavera Real",
                  "textoBotonCompra": "Llevar a mi bolsa"
                }
                """;
        mvc.perform(post("/emprendedor/constructor/publicar")
                        .contentType("application/json")
                        .content(configPublicar)
                        .with(como(maria)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        mvc.perform(get("/tienda/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Moda de autor en MariaDB")))
                .andExpect(content().string(containsString("Colección Primavera Real")))
                .andExpect(content().string(containsString("Llevar a mi bolsa")));
    }

    @Test
    void chatYRecuperacionDeContrasenaIntegradosConMariaDbReal() throws Exception {
        Usuario carlos = usuarios.findByCorreo("cliente@cabore.co").orElseThrow();
        Usuario maria = usuarios.findByCorreo("emprendedor@cabore.co").orElseThrow();
        Usuario dianaCliente = usuarios.findById(7).orElseThrow();

        // 1) Conversación #1 existente en cabore_restaurada entre Boutique María (id=1) y Carlos Comprador (id=2)
        mvc.perform(get("/chat").param("c", "1").with(como(carlos)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Boutique María")));

        // 2) Carlos envía un nuevo mensaje en la conversación #1 (se revierte al finalizar el test)
        mvc.perform(post("/chat/enviar")
                        .param("conversacion_id", "1")
                        .param("contenido", "Mensaje de prueba sobre MariaDB real")
                        .with(como(carlos)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chat?conversacion_id=1"));

        // 3) María (dueña de la tienda 1) lo lee
        mvc.perform(get("/chat").param("c", "1").with(como(maria)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mensaje de prueba sobre MariaDB real")));

        // 4) Otro usuario ajeno (id=7) recibe 403 Forbidden al intentar abrir la conversación #1
        mvc.perform(get("/chat").param("c", "1").with(como(dianaCliente)))
                .andExpect(status().isForbidden());

        // 5) Solicitud de recuperación de contraseña persiste reset_token y reset_expira en MariaDB
        mvc.perform(post("/olvide-password").with(csrf()).param("correo", "cliente@cabore.co"))
                .andExpect(status().isOk());
        Usuario carlosActualizado = usuarios.findByCorreo("cliente@cabore.co").orElseThrow();
        assertThat(carlosActualizado.getResetToken()).isNotNull().hasSize(64);
        assertThat(carlosActualizado.getResetExpira()).isNotNull();
    }

    private static RequestPostProcessor como(Usuario u) {
        UsuarioPrincipal up = new UsuarioPrincipal(u);
        RequestPostProcessor auth = authentication(
                new UsernamePasswordAuthenticationToken(up, null, up.getAuthorities()));
        return request -> csrf().postProcessRequest(auth.postProcessRequest(request));
    }
}
