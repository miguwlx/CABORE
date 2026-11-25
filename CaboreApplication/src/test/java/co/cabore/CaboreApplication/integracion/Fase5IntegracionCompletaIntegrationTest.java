package co.cabore.CaboreApplication.integracion;

import co.cabore.CaboreApplication.config.UsuarioPrincipal;
import co.cabore.CaboreApplication.model.*;
import co.cabore.CaboreApplication.repository.*;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FASE 5 — Revisión visual, integración completa entre todos los roles y flujos de extremo a extremo.
 * Verifica la conexión real entre Registro, Login, Emprendedor, Administrador, Tienda Pública,
 * Catálogo Cliente, Carrito, Checkout, Inventario, Facturación, Reseñas, Chat y Recuperación de contraseña.
 */
@SpringBootTest(properties = "cabore.admin.setup-key=ClaveInstalacionTest2026")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Fase5IntegracionCompletaIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired TiendaRepository tiendas;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    @Autowired PedidoRepository pedidos;
    @Autowired ConversacionRepository conversaciones;
    @Autowired MensajeRepository mensajes;
    @Autowired ResenaRepository resenas;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void cicloDeVidaCompletoEntreEmprendedorAdministradorYCliente() throws Exception {
        // 0. Preparar categoría y administrador
        Categoria catArtesania = new Categoria();
        catArtesania.setNombre("Artesanías");
        catArtesania.setIcono("🏺");
        catArtesania = categorias.save(catArtesania);

        Usuario admin = new Usuario();
        admin.setNombre("Diana Admin");
        admin.setCorreo("admin.fase5@cabore.co");
        admin.setContrasena(passwordEncoder.encode("AdminClave123"));
        admin.setRol(Rol.administrador);
        admin.setActivo(true);
        admin = usuarios.save(admin);

        // 1. Registro real de emprendedor y cliente desde /registro (inicia sesión automáticamente y redirige por rol)
        mvc.perform(post("/registro").with(csrf())
                        .param("nombre", "Mateo Artesano")
                        .param("correo", "mateo.fase5@cabore.co")
                        .param("contrasena", "MateoClave123")
                        .param("rol", "emprendedor"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/emprendedor"));

        mvc.perform(post("/registro").with(csrf())
                        .param("nombre", "Sofía Compradora")
                        .param("correo", "sofia.fase5@cabore.co")
                        .param("contrasena", "SofiaClave123")
                        .param("rol", "cliente"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente"));

        Usuario emp = usuarios.findByCorreo("mateo.fase5@cabore.co").orElseThrow();
        Usuario cli = usuarios.findByCorreo("sofia.fase5@cabore.co").orElseThrow();

        // 2. Emprendedor configura su tienda y publica un producto nuevo (queda pendiente de moderación)
        mvc.perform(post("/emprendedor/tienda")
                        .param("seccion", "info")
                        .param("nombre", "Taller Mateo")
                        .param("descripcion", "Cerámica de Ráquira")
                        .param("ciudad", "Ráquira")
                        .with(como(emp)))
                .andExpect(status().is3xxRedirection());

        Tienda tiendaMateo = tiendas.findByUsuarioId(emp.getId()).orElseThrow();

        mvc.perform(multipart("/emprendedor/productos")
                        .param("formCompleto", "1")
                        .param("nombre", "Vasija Ráquira")
                        .param("descripcion", "Hecha a mano")
                        .param("precio", "90000")
                        .param("precio_oferta", "75000")
                        .param("categoria_id", catArtesania.getId().toString())
                        .param("stock", "10")
                        .param("destacado", "true")
                        .param("activo", "true")
                        .with(como(emp)))
                .andExpect(status().is3xxRedirection());

        Producto vasija = productos.findByTiendaIdOrderByIdDesc(tiendaMateo.getId()).get(0);
        assertThat(vasija.getEstadoVerificacion()).isEqualTo(EstadoVerificacion.pendiente);

        // Antes de la aprobación del administrador, el producto NO aparece en la tienda pública ni en el catálogo
        mvc.perform(get("/tienda/" + tiendaMateo.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Iniciar sesión")))
                .andExpect(content().string(not(containsString("Vasija Ráquira"))));

        // 3. Administrador aprueba el producto desde el módulo de verificación
        mvc.perform(post("/admin/productos/verificar/ajax")
                        .param("producto_id", vasija.getId().toString())
                        .param("accion", "aprobar")
                        .with(como(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.estado").value("aprobado"));

        // 4. Cliente explora la tienda pública y la ficha del producto (con navegación contextual y botón de chat)
        mvc.perform(get("/tienda/" + tiendaMateo.getId()).with(como(cli)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vasija Ráquira")))
                .andExpect(content().string(containsString("← Volver al catálogo")))
                .andExpect(content().string(containsString("/chat/iniciar?tiendaId=" + tiendaMateo.getId())));

        mvc.perform(get("/cliente/producto/" + vasija.getId()).with(como(cli)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vasija Ráquira")))
                .andExpect(content().string(containsString("Chatear con la tienda")));

        // 5. Cliente inicia chat con la tienda y envía consulta; el emprendedor la recibe en su panel y responde
        String chatRedir = mvc.perform(get("/chat/iniciar")
                        .param("tiendaId", tiendaMateo.getId().toString())
                        .with(como(cli)))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        Integer convId = Integer.parseInt(chatRedir.substring("/chat?conversacion_id=".length()));

        mvc.perform(post("/chat/enviar")
                        .param("conversacion_id", convId.toString())
                        .param("contenido", "Hola Mateo, ¿viene bien empacada?")
                        .with(como(cli)))
                .andExpect(status().is3xxRedirection());

        assertThat(mensajes.contarNoLeidosTienda(tiendaMateo.getId(), emp.getId())).isEqualTo(1L);

        // Emprendedor responde y el cliente ve la insignia de mensajes no leídos en su barra superior
        mvc.perform(post("/chat/enviar")
                        .param("conversacion_id", convId.toString())
                        .param("contenido", "¡Sí Sofía! Va con doble protección.")
                        .with(como(emp)))
                .andExpect(status().is3xxRedirection());

        assertThat(mensajes.contarNoLeidosCliente(cli.getId())).isEqualTo(1L);
        mvc.perform(get("/cliente").with(como(cli)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("mensajesNoLeidosCount", 1));

        // 6. Cliente agrega 2 unidades al carrito y confirma el pedido en checkout
        mvc.perform(post("/cliente/carrito/agregar")
                        .param("productoId", vasija.getId().toString())
                        .param("cantidad", "2")
                        .with(como(cli)))
                .andExpect(status().is3xxRedirection());

        mvc.perform(post("/cliente/checkout")
                        .param("direccion", "Av. Siempre Viva 742, Bogotá")
                        .param("telefono", "3157778899")
                        .param("notas", "Entregar en portería")
                        .with(como(cli)))
                .andExpect(status().is3xxRedirection());

        // Stock descontado en inventario (10 - 2 = 8)
        assertThat(productos.findById(vasija.getId()).orElseThrow().getStock()).isEqualTo(8);

        Pedido pedido = pedidos.findByUsuarioIdOrderByCreadoEnDesc(cli.getId()).get(0);
        assertThat(pedido.getTotal()).isEqualByComparingTo("150000.00"); // 2 * 75.000 oferta

        // 7. Emprendedor marca el pedido como entregado desde su panel
        mvc.perform(post("/emprendedor/pedidos/estado/ajax")
                        .param("pedido_id", pedido.getId().toString())
                        .param("estado", "entregado")
                        .with(como(emp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.estado").value("entregado"));

        // 8. Ahora que el pedido está entregado, el cliente publica una reseña verificada
        mvc.perform(post("/cliente/resenas")
                        .param("productoId", vasija.getId().toString())
                        .param("calificacion", "5")
                        .param("comentario", "Excelente cerámica, llegó perfecta.")
                        .with(como(cli)))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/cliente/producto/" + vasija.getId()).with(como(cli)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Excelente cerámica, llegó perfecta.")));

        // Perfil del cliente muestra el contador de pedidos realizados (totalPedidos = 1)
        mvc.perform(get("/cliente/perfil").with(como(cli)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalPedidos", 1))
                .andExpect(content().string(containsString("Sofía Compradora")));

        // Al actualizar el nombre en /cliente/perfil/datos, la sesión activa se refresca inmediatamente
        mvc.perform(post("/cliente/perfil/datos")
                        .param("nombre", "Valeria Renovada")
                        .param("telefono", "3157778899")
                        .param("ciudad", "Bogotá")
                        .param("direccion", "Av. Siempre Viva 742")
                        .with(como(cli)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente/perfil"));
        assertThat(usuarios.findById(cli.getId()).orElseThrow().getNombre()).isEqualTo("Valeria Renovada");

        // Favoritos muestra las calificaciones agregadas (ratingPromedio = 5.0, totalResenas = 1)
        mvc.perform(post("/cliente/favoritos/toggle")
                        .param("productoId", vasija.getId().toString())
                        .with(como(cli)))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/cliente/favoritos").with(como(cli)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vasija Ráquira")))
                .andExpect(content().string(containsString("(1)")));

        // 9. Recuperación de contraseña del cliente e inicio de sesión con la nueva contraseña (insensible a mayúsculas en correo)
        mvc.perform(post("/olvide-password").with(csrf()).param("correo", "SOFIA.FASE5@CABORE.CO"))
                .andExpect(status().isOk());

        String token = usuarios.findById(cli.getId()).orElseThrow().getResetToken();
        assertThat(token).isNotBlank();

        mvc.perform(post("/restablecer-password").with(csrf())
                        .param("token", token)
                        .param("nueva_contrasena", "SofiaNueva2026!")
                        .param("confirmar_contrasena", "SofiaNueva2026!"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("actualizada")));

        mvc.perform(post("/login").with(csrf())
                        .param("correo", "SOFIA.FASE5@CABORE.CO")
                        .param("contrasena", "SofiaNueva2026!"))
                .andExpect(authenticated().withRoles("CLIENTE"))
                .andExpect(redirectedUrl("/cliente"));
    }

    @Test
    void creacionControladaDeAdministradorMedianteClaveDeInstalacion() throws Exception {
        // 1) Formulario /crear-admin carga correctamente
        mvc.perform(get("/crear-admin"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Crear administrador")));

        // 2) Clave de instalación incorrecta es rechazada
        mvc.perform(post("/crear-admin").with(csrf())
                        .param("clave_instalacion", "clave-falsa")
                        .param("nombre", "Admin Intruso")
                        .param("correo", "intruso@cabore.co")
                        .param("contrasena", "ClaveSegura123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Clave de instalación incorrecta.")));
        assertThat(usuarios.findByCorreo("intruso@cabore.co")).isEmpty();

        // 3) Clave de instalación correcta crea el administrador y permite iniciar sesión hacia /admin
        mvc.perform(post("/crear-admin").with(csrf())
                        .param("clave_instalacion", "ClaveInstalacionTest2026")
                        .param("nombre", "Nuevo Admin Caboré")
                        .param("correo", "nuevo.admin@cabore.co")
                        .param("contrasena", "AdminFuerte2026!"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cuenta de administrador creada correctamente")));

        Usuario adminCreado = usuarios.findByCorreo("nuevo.admin@cabore.co").orElseThrow();
        assertThat(adminCreado.getRol()).isEqualTo(Rol.administrador);

        mvc.perform(post("/login").with(csrf())
                        .param("correo", "nuevo.admin@cabore.co")
                        .param("contrasena", "AdminFuerte2026!"))
                .andExpect(authenticated().withRoles("ADMINISTRADOR"))
                .andExpect(redirectedUrl("/admin"));
    }

    private static RequestPostProcessor como(Usuario u) {
        UsuarioPrincipal up = new UsuarioPrincipal(u);
        RequestPostProcessor auth = authentication(
                new UsernamePasswordAuthenticationToken(up, null, up.getAuthorities()));
        return request -> csrf().postProcessRequest(auth.postProcessRequest(request));
    }
}
