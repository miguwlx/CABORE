package co.cabore.CaboreApplication.service;

import co.cabore.CaboreApplication.model.*;
import co.cabore.CaboreApplication.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de Chat entre clientes y tiendas (FASE 4 — Miguel).
 *
 * Migra chat.php, iniciar_chat.php y enviar_mensaje.php:
 *  - Inicio o reutilización de conversación única por par (tienda_id, cliente_id).
 *  - Listado de conversaciones propias con último mensaje y conteo de no leídos.
 *  - Marcado automático como leídos al abrir la conversación activa.
 *  - Envío de mensajes actualizando actualizado_en de la conversación.
 *  - Protección estricta: ningún usuario puede leer ni escribir en conversaciones ajenas.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversacionRepository conversaciones;
    private final MensajeRepository mensajes;
    private final TiendaRepository tiendas;
    private final UsuarioRepository usuarios;

    public record ConversacionResumenVista(
            Integer id,
            LocalDateTime actualizadoEn,
            String nombreOtro,
            String inicialOtro,
            String logoOtro,
            Integer tiendaIdOtro,
            String ultimoMensaje,
            long noLeidos
    ) {}

    public record VistaChat(
            boolean esEmprendedor,
            String volverUrl,
            List<ConversacionResumenVista> conversaciones,
            ConversacionResumenVista conversacionActiva,
            List<Mensaje> mensajes
    ) {}

    /**
     * Inicia (o recupera si ya existe) la conversación del cliente con la tienda
     * indicada. Devuelve null si la tienda no existe (igual que iniciar_chat.php).
     */
    @Transactional
    public Integer iniciarConversacion(Integer clienteId, Integer tiendaId) {
        if (tiendaId == null || tiendaId <= 0) {
            return null;
        }
        Optional<Tienda> tiendaOpt = tiendas.findById(tiendaId);
        if (tiendaOpt.isEmpty()) {
            return null;
        }
        Tienda tienda = tiendaOpt.get();

        return conversaciones.findByTiendaIdAndClienteId(tienda.getId(), clienteId)
                .map(Conversacion::getId)
                .orElseGet(() -> {
                    Conversacion nueva = new Conversacion();
                    nueva.setTienda(tienda);
                    nueva.setCliente(usuarios.getReferenceById(clienteId));
                    nueva.setActualizadoEn(LocalDateTime.now());
                    return conversaciones.save(nueva).getId();
                });
    }

    /**
     * Inicia (o recupera si ya existe) la conversación de la tienda del emprendedor
     * con un cliente específico (por ejemplo, desde la tabla de pedidos recibidos).
     */
    @Transactional
    public Integer iniciarConversacionConCliente(Integer emprendedorUsuarioId, Integer clienteId) {
        if (clienteId == null || clienteId <= 0) {
            return null;
        }
        Optional<Tienda> tiendaOpt = tiendas.findByUsuarioId(emprendedorUsuarioId);
        if (tiendaOpt.isEmpty()) {
            return null;
        }
        Optional<Usuario> clienteOpt = usuarios.findById(clienteId)
                .filter(u -> u.getRol() == Rol.cliente);
        if (clienteOpt.isEmpty()) {
            return null;
        }
        Tienda tienda = tiendaOpt.get();
        Usuario cliente = clienteOpt.get();

        return conversaciones.findByTiendaIdAndClienteId(tienda.getId(), cliente.getId())
                .map(Conversacion::getId)
                .orElseGet(() -> {
                    Conversacion nueva = new Conversacion();
                    nueva.setTienda(tienda);
                    nueva.setCliente(cliente);
                    nueva.setActualizadoEn(LocalDateTime.now());
                    return conversaciones.save(nueva).getId();
                });
    }

    /**
     * Carga la lista de conversaciones del usuario autenticado, marca como leídos
     * los mensajes recibidos en la conversación activa y devuelve su historial.
     */
    @Transactional
    public VistaChat cargarChat(Integer usuarioId, Rol rol, Integer conversacionIdSolicitado) {
        if (rol != Rol.cliente && rol != Rol.emprendedor) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Rol no autorizado para el chat.");
        }

        // Si pidió una conversación específica, verificar que exista y le pertenezca
        if (conversacionIdSolicitado != null && conversacionIdSolicitado > 0) {
            Conversacion solicitada = conversaciones.findById(conversacionIdSolicitado)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversación no encontrada."));
            if (!perteneceAUsuario(solicitada, usuarioId, rol)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "No tienes permiso para acceder a esta conversación.");
            }
        }

        List<Conversacion> listaPropia;
        boolean esEmprendedor = (rol == Rol.emprendedor);
        if (esEmprendedor) {
            Optional<Tienda> miTienda = tiendas.findByUsuarioId(usuarioId);
            listaPropia = miTienda
                    .map(t -> conversaciones.findByTiendaIdOrderByActualizadoEnDesc(t.getId()))
                    .orElseGet(List::of);
        } else {
            listaPropia = conversaciones.findByClienteIdOrderByActualizadoEnDesc(usuarioId);
        }

        Conversacion activaEntidad = null;
        if (conversacionIdSolicitado != null && conversacionIdSolicitado > 0) {
            for (Conversacion c : listaPropia) {
                if (c.getId().equals(conversacionIdSolicitado)) {
                    activaEntidad = c;
                    break;
                }
            }
        }
        if (activaEntidad == null && !listaPropia.isEmpty()) {
            activaEntidad = listaPropia.get(0);
        }

        List<Mensaje> historial = List.of();
        if (activaEntidad != null) {
            mensajes.marcarComoLeidos(activaEntidad.getId(), usuarioId);
            historial = mensajes.findByConversacionIdOrderByIdAsc(activaEntidad.getId());
        }

        List<ConversacionResumenVista> resumenes = new ArrayList<>(listaPropia.size());
        ConversacionResumenVista activaVista = null;

        for (Conversacion c : listaPropia) {
            String nombreOtro = esEmprendedor
                    ? (c.getCliente() != null ? c.getCliente().getNombre() : "Cliente")
                    : (c.getTienda() != null ? c.getTienda().getNombre() : "Tienda");
            String inicial = (nombreOtro == null || nombreOtro.isBlank())
                    ? "U"
                    : nombreOtro.trim().substring(0, 1).toUpperCase();
            String logoOtro = esEmprendedor
                    ? null
                    : (c.getTienda() != null ? c.getTienda().getRutaLogo() : null);
            Integer tiendaIdOtro = esEmprendedor
                    ? null
                    : (c.getTienda() != null ? c.getTienda().getId() : null);

            String ultimo = mensajes.findFirstByConversacionIdOrderByIdDesc(c.getId())
                    .map(Mensaje::getContenido)
                    .orElse(null);

            boolean esLaActiva = activaEntidad != null && c.getId().equals(activaEntidad.getId());
            long noLeidos = esLaActiva ? 0L : mensajes.contarNoLeidosEnConversacion(c.getId(), usuarioId);

            ConversacionResumenVista rv = new ConversacionResumenVista(
                    c.getId(),
                    c.getActualizadoEn(),
                    nombreOtro,
                    inicial,
                    logoOtro,
                    tiendaIdOtro,
                    ultimo,
                    noLeidos
            );
            resumenes.add(rv);
            if (esLaActiva) {
                activaVista = rv;
            }
        }

        String volverUrl = esEmprendedor ? "/emprendedor" : "/cliente";
        return new VistaChat(esEmprendedor, volverUrl, resumenes, activaVista, historial);
    }

    /**
     * Envía un mensaje dentro de una conversación propia y actualiza actualizado_en.
     */
    @Transactional
    public Mensaje enviarMensaje(Integer usuarioId, Rol rol, Integer conversacionId, String contenido) {
        if (conversacionId == null || conversacionId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conversación inválida.");
        }
        Conversacion conv = conversaciones.findById(conversacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversación no encontrada."));

        if (!perteneceAUsuario(conv, usuarioId, rol)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tienes permiso para enviar mensajes en esta conversación.");
        }

        if (contenido == null || contenido.trim().isEmpty()) {
            return null;
        }

        Mensaje m = new Mensaje();
        m.setConversacion(conv);
        m.setEmisor(usuarios.getReferenceById(usuarioId));
        m.setContenido(contenido.trim());
        m.setLeido(false);
        Mensaje guardado = mensajes.save(m);

        conv.setActualizadoEn(LocalDateTime.now());
        conversaciones.save(conv);
        return guardado;
    }

    public boolean perteneceAUsuario(Conversacion c, Integer usuarioId, Rol rol) {
        if (c == null || usuarioId == null) return false;
        if (rol == Rol.cliente) {
            return c.getCliente() != null && usuarioId.equals(c.getCliente().getId());
        }
        if (rol == Rol.emprendedor) {
            return c.getTienda() != null
                    && c.getTienda().getUsuario() != null
                    && usuarioId.equals(c.getTienda().getUsuario().getId());
        }
        return false;
    }
}
