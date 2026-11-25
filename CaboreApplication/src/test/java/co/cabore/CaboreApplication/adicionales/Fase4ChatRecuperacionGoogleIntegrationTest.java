package co.cabore.CaboreApplication.adicionales;

import co.cabore.CaboreApplication.config.UsuarioPrincipal;
import co.cabore.CaboreApplication.model.*;
import co.cabore.CaboreApplication.repository.*;
import co.cabore.CaboreApplication.service.GoogleAuthService;
import co.cabore.CaboreApplication.service.GoogleTokenVerifier;
import co.cabore.CaboreApplication.service.OperacionInvalidaException;
import co.cabore.CaboreApplication.service.RecuperacionPasswordService;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FASE 4 — Chat entre clientes y tiendas, Recuperación de contraseña y Autenticación con Google.
 * Pruebas integrales de extremo a extremo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Fase4ChatRecuperacionGoogleIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired TiendaRepository tiendas;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    @Autowired ConversacionRepository conversaciones;
    @Autowired MensajeRepository mensajes;
    @Autowired PasswordEncoder passwordEncoder;

    Usuario emp1, emp2, cliente1, cliente2;
    Tienda tienda1, tienda2;

    @BeforeEach
    void seed() {
        emp1 = crearUsuario("María Emprendedora", "maria@chat.co", Rol.emprendedor);
        emp2 = crearUsuario("Pedro Otro", "pedro@chat.co", Rol.emprendedor);
        cliente1 = crearUsuario("Carlos Cliente", "carlos@chat.co", Rol.cliente);
        cliente2 = crearUsuario("Ana Intrusa", "ana@chat.co", Rol.cliente);

        tienda1 = new Tienda();
        tienda1.setUsuario(emp1);
        tienda1.setNombre("Boutique María");
        tienda1.setDescripcion("Artesanías");
        tienda1.setActivo(true);
        tienda1 = tiendas.save(tienda1);

        tienda2 = new Tienda();
        tienda2.setUsuario(emp2);
        tienda2.setNombre("Tienda Pedro");
        tienda2.setActivo(true);
        tienda2 = tiendas.save(tienda2);

        Categoria cat = new Categoria();
        cat.setNombre("General");
        cat = categorias.save(cat);

        Producto p = new Producto();
        p.setNombre("Collar Oro");
        p.setPrecio(new BigDecimal("50000.00"));
        p.setStock(5);
        p.setCategoriaId(cat.getId());
        p.setActivo(true);
        p.setEstadoVerificacion(EstadoVerificacion.aprobado);
        p.setTienda(tienda1);
        productos.save(p);
    }

    /* ── 1. Chat entre cliente y tienda + integración con tienda pública ── */

    @Test
    void flujoCompletoDeChatYBloqueoDeConversacionesAjenas() throws Exception {
        // 1) La tienda pública muestra el botón "Chatear con la tienda" apuntando a /chat/iniciar?tiendaId=...
        mvc.perform(get("/tienda/" + tienda1.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/chat/iniciar?tiendaId=" + tienda1.getId())));

        // 2) Cliente 1 inicia conversación con Boutique María
        String redirectUrl = mvc.perform(get("/chat/iniciar")
                        .param("tiendaId", tienda1.getId().toString())
                        .with(como(cliente1)))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(redirectUrl).startsWith("/chat?conversacion_id=");
        Integer convId = Integer.parseInt(redirectUrl.substring("/chat?conversacion_id=".length()));

        // Si vuelve a llamar a /chat/iniciar con la misma tienda, reutiliza la misma conversación
        mvc.perform(get("/chat/iniciar")
                        .param("tiendaId", tienda1.getId().toString())
                        .with(como(cliente1)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chat?conversacion_id=" + convId));

        // 3) Un emprendedor NO puede iniciar chat como cliente (403 Forbidden)
        mvc.perform(get("/chat/iniciar")
                        .param("tiendaId", tienda1.getId().toString())
                        .with(como(emp1)))
                .andExpect(status().isForbidden());

        // 4) Cliente 1 envía un mensaje a la tienda
        mvc.perform(post("/chat/enviar")
                        .param("conversacion_id", convId.toString())
                        .param("contenido", "Hola María, ¿tienes envío a Medellín?")
                        .with(como(cliente1)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chat?conversacion_id=" + convId));

        assertThat(mensajes.contarNoLeidosEnConversacion(convId, emp1.getId())).isEqualTo(1L);

        // 5) La emprendedora (María) abre el chat: ve el mensaje de Carlos y se marca como leído
        mvc.perform(get("/chat").param("conversacion_id", convId.toString()).with(como(emp1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Carlos Cliente")))
                .andExpect(content().string(containsString("Hola María, ¿tienes envío a Medellín?")));

        assertThat(mensajes.contarNoLeidosEnConversacion(convId, emp1.getId())).isZero();

        // 6) María responde y Carlos recibe el mensaje
        mvc.perform(post("/chat/enviar")
                        .param("conversacion_id", convId.toString())
                        .param("contenido", "¡Sí Carlos! Despachamos hoy mismo.")
                        .with(como(emp1)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chat?conversacion_id=" + convId));

        mvc.perform(get("/chat").param("conversacion_id", convId.toString()).with(como(cliente1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Boutique María")))
                .andExpect(content().string(containsString("¡Sí Carlos! Despachamos hoy mismo.")));

        // 7) SEGURIDAD: Cliente 2 (Ana Intrusa) y Emprendedor 2 (Pedro) NO pueden leer ni escribir en esa conversación
        mvc.perform(get("/chat").param("conversacion_id", convId.toString()).with(como(cliente2)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/chat").param("conversacion_id", convId.toString()).with(como(emp2)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/chat/enviar")
                        .param("conversacion_id", convId.toString())
                        .param("contenido", "Mensaje intruso")
                        .with(como(cliente2)))
                .andExpect(status().isForbidden());

        assertThat(mensajes.findByConversacionIdOrderByIdAsc(convId)).hasSize(2);

        // 8) Emprendedor también puede iniciar conversación con un cliente desde su panel (/chat/iniciar-cliente)
        String redirEmp = mvc.perform(get("/chat/iniciar-cliente")
                        .param("clienteId", cliente2.getId().toString())
                        .with(como(emp1)))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(redirEmp).startsWith("/chat?conversacion_id=");

        // Y el endpoint AJAX de mensajes devuelve el historial en formato JSON
        mvc.perform(get("/chat/mensajes/ajax")
                        .param("conversacion_id", convId.toString())
                        .with(como(cliente1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.mensajes").isArray());
    }

    /* ── 2. Recuperación y restablecimiento de contraseña ──────────────── */

    @Test
    void flujoCompletoDeRecuperacionExpiracionYRestablecimientoDeContrasena() throws Exception {
        // 1) Formulario público disponible en /olvide-password y alias /olvide_password.html
        mvc.perform(get("/olvide-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("¿Olvidaste tu")));

        // 2) Correo inválido muestra aviso de error
        mvc.perform(post("/olvide-password").with(csrf()).param("correo", "correo-invalido"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ingresa un correo válido.")));

        // 3) Correo inexistente devuelve el MISMO mensaje genérico (no filtra existencia de cuentas)
        mvc.perform(post("/olvide-password").with(csrf()).param("correo", "noexiste@chat.co"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(RecuperacionPasswordService.MENSAJE_GENERICO)));

        // 4) Correo existente genera token seguro de 64 caracteres hex con vigencia de 1 hora
        mvc.perform(post("/olvide-password").with(csrf()).param("correo", cliente1.getCorreo()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(RecuperacionPasswordService.MENSAJE_GENERICO)));

        Usuario conToken = usuarios.findById(cliente1.getId()).orElseThrow();
        String token = conToken.getResetToken();
        assertThat(token).isNotNull().hasSize(64).matches("^[0-9a-f]{64}$");
        assertThat(conToken.getResetExpira()).isAfter(LocalDateTime.now().plusMinutes(50));

        // 5) GET /restablecer-password con token válido muestra el formulario
        mvc.perform(get("/restablecer-password").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Crea tu")))
                .andExpect(content().string(containsString("Guardar nueva contraseña")));

        // 6) Validaciones de contraseña: menos de 8 caracteres o no coinciden
        mvc.perform(post("/restablecer-password").with(csrf())
                        .param("token", token)
                        .param("nueva_contrasena", "corta")
                        .param("confirmar_contrasena", "corta"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("La contraseña debe tener al menos 8 caracteres.")));

        mvc.perform(post("/restablecer-password").with(csrf())
                        .param("token", token)
                        .param("nueva_contrasena", "NuevaSegura2026!")
                        .param("confirmar_contrasena", "OtraDistinta2026!"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Las contraseñas no coinciden.")));

        // 7) Restablecimiento exitoso: actualiza hash BCrypt e invalida token
        mvc.perform(post("/restablecer-password").with(csrf())
                        .param("token", token)
                        .param("nueva_contrasena", "NuevaSegura2026!")
                        .param("confirmar_contrasena", "NuevaSegura2026!"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Contraseña")))
                .andExpect(content().string(containsString("actualizada")));

        Usuario actualizado = usuarios.findById(cliente1.getId()).orElseThrow();
        assertThat(actualizado.getResetToken()).isNull();
        assertThat(actualizado.getResetExpira()).isNull();
        assertThat(passwordEncoder.matches("NuevaSegura2026!", actualizado.getContrasena())).isTrue();

        // 8) Reutilizar el mismo token ya consumido es rechazado
        mvc.perform(get("/restablecer-password").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Este enlace")));

        // 9) Un token expirado es rechazado al intentar cambiar la contraseña
        actualizado.setResetToken("a".repeat(64));
        actualizado.setResetExpira(LocalDateTime.now().minusMinutes(5));
        usuarios.saveAndFlush(actualizado);

        mvc.perform(post("/restablecer-password").with(csrf())
                        .param("token", "a".repeat(64))
                        .param("nueva_contrasena", "OtraClave9999")
                        .param("confirmar_contrasena", "OtraClave9999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Este enlace")));
        assertThat(passwordEncoder.matches("NuevaSegura2026!",
                usuarios.findById(cliente1.getId()).orElseThrow().getContrasena())).isTrue();
    }

    /* ── 3. Autenticación con Google (registro, vinculación y bloqueo) ─── */

    @Test
    void autenticacionConGoogleRegistroVinculacionDeCuentaYBloqueoDeSuspendidos() throws Exception {
        // 0) /login y /registro inyectan el GOOGLE_CLIENT_ID oficial del proyecto en #g_id_onload
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("853981485167-vsbi9h44ktc32d6c6ppipnls9fc6iqh7.apps.googleusercontent.com")));
        mvc.perform(get("/registro"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("853981485167-vsbi9h44ktc32d6c6ppipnls9fc6iqh7.apps.googleusercontent.com")));

        // 1) Endpoint HTTP /auth/google sin token responde JSON controlado
        mvc.perform(post("/auth/google").with(csrf())
                        .param("credential", "")
                        .param("rol", "cliente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error").value("Falta el token de Google."));

        // 2) Verificación de lógica de negocio con GoogleTokenVerifier simulado:
        //    a) Registro de nuevo cliente con Google
        GoogleTokenVerifier verifierNuevoCliente = cred -> new GoogleTokenVerifier.DatosGoogle(
                "google-sub-1001", "nuevo.google@chat.co", "Camila Google", true);
        GoogleAuthService serviceCliente = new GoogleAuthService(verifierNuevoCliente, usuarios, tiendas);

        Usuario uCliente = serviceCliente.autenticar("jwt-simulado-1", "cliente");
        assertThat(uCliente.getId()).isNotNull();
        assertThat(uCliente.getGoogleId()).isEqualTo("google-sub-1001");
        assertThat(uCliente.getRol()).isEqualTo(Rol.cliente);
        assertThat(GoogleAuthService.destinoPorRol(uCliente.getRol())).isEqualTo("/cliente");

        //    b) Registro de nuevo emprendedor con Google crea automáticamente su tienda
        GoogleTokenVerifier verifierNuevoEmp = cred -> new GoogleTokenVerifier.DatosGoogle(
                "google-sub-2002", "emp.google@chat.co", "Julián Artesano", true);
        GoogleAuthService serviceEmp = new GoogleAuthService(verifierNuevoEmp, usuarios, tiendas);

        Usuario uEmp = serviceEmp.autenticar("jwt-simulado-2", "emprendedor");
        assertThat(uEmp.getRol()).isEqualTo(Rol.emprendedor);
        assertThat(tiendas.findByUsuarioId(uEmp.getId())).isPresent();
        assertThat(GoogleAuthService.destinoPorRol(uEmp.getRol())).isEqualTo("/emprendedor");

        //    c) Usuario existente por correo sin google_id: vincula su google_id conservando su rol original
        assertThat(cliente1.getGoogleId()).isNull();
        GoogleTokenVerifier verifierExistente = cred -> new GoogleTokenVerifier.DatosGoogle(
                "google-sub-carlos", cliente1.getCorreo(), "Carlos Cliente", true);
        GoogleAuthService serviceExistente = new GoogleAuthService(verifierExistente, usuarios, tiendas);

        Usuario vinculado = serviceExistente.autenticar("jwt-simulado-3", "emprendedor");
        assertThat(vinculado.getId()).isEqualTo(cliente1.getId());
        assertThat(vinculado.getGoogleId()).isEqualTo("google-sub-carlos");
        assertThat(vinculado.getRol()).isEqualTo(Rol.cliente); // conserva su rol existente

        //    d) Cuenta suspendida es rechazada aunque el token de Google sea válido
        cliente1.setActivo(false);
        usuarios.saveAndFlush(cliente1);
        assertThatThrownBy(() -> serviceExistente.autenticar("jwt-simulado-3", "cliente"))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessage("Esta cuenta está suspendida.");
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
        u.setContrasena(passwordEncoder.encode("ClaveInicial123"));
        u.setRol(rol);
        u.setActivo(true);
        return usuarios.save(u);
    }
}
