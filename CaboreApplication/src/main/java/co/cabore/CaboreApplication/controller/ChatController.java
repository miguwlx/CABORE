package co.cabore.CaboreApplication.controller;

import co.cabore.CaboreApplication.config.UsuarioPrincipal;
import co.cabore.CaboreApplication.model.Rol;
import co.cabore.CaboreApplication.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controlador del módulo de Chat entre clientes y tiendas (FASE 4 — Miguel).
 *
 * Endpoints:
 *  - GET  /chat?conversacion_id={id}         (chat.php)
 *  - GET  /chat/iniciar?tiendaId={id}        (iniciar_chat.php, también acepta tienda_id)
 *  - POST /chat/enviar                       (enviar_mensaje.php)
 */
@Controller
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public String verChat(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @RequestParam(name = "conversacion_id", required = false) Integer conversacionId,
            @RequestParam(name = "c", required = false) Integer conversacionIdAlias,
            Model model) {

        Integer idSolicitado = conversacionId != null ? conversacionId : conversacionIdAlias;
        ChatService.VistaChat vista = chatService.cargarChat(yo.getId(), yo.getRol(), idSolicitado);
        model.addAttribute("usuarioId", yo.getId());
        model.addAttribute("miNombre", yo.getNombre());
        model.addAttribute("esEmprendedor", vista.esEmprendedor());
        model.addAttribute("volverA", vista.volverUrl());
        model.addAttribute("conversaciones", vista.conversaciones());
        model.addAttribute("conversacionActiva", vista.conversacionActiva());
        model.addAttribute("conversacionId",
                vista.conversacionActiva() != null ? vista.conversacionActiva().id() : 0);
        model.addAttribute("mensajes", vista.mensajes());
        return "chat";
    }

    @GetMapping("/iniciar")
    public String iniciar(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @RequestParam(name = "tiendaId", required = false) Integer tiendaId,
            @RequestParam(name = "tienda_id", required = false) Integer tiendaIdLegacy) {

        if (yo.getRol() != Rol.cliente) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo los clientes pueden iniciar una conversación con una tienda.");
        }

        Integer idTienda = tiendaId != null ? tiendaId : tiendaIdLegacy;
        Integer conversacionId = chatService.iniciarConversacion(yo.getId(), idTienda);
        if (conversacionId == null) {
            return "redirect:/cliente";
        }
        return "redirect:/chat?conversacion_id=" + conversacionId;
    }

    @GetMapping("/iniciar-cliente")
    public String iniciarConCliente(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @RequestParam(name = "clienteId", required = false) Integer clienteId) {

        if (yo.getRol() != Rol.emprendedor) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo los emprendedores pueden iniciar una conversación con un cliente desde su tienda.");
        }

        Integer conversacionId = chatService.iniciarConversacionConCliente(yo.getId(), clienteId);
        if (conversacionId == null) {
            return "redirect:/emprendedor?tab=ventas";
        }
        return "redirect:/chat?conversacion_id=" + conversacionId;
    }

    @PostMapping("/enviar")
    public String enviar(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @RequestParam("conversacion_id") Integer conversacionId,
            @RequestParam(name = "contenido", defaultValue = "") String contenido) {

        chatService.enviarMensaje(yo.getId(), yo.getRol(), conversacionId, contenido);
        return "redirect:/chat?conversacion_id=" + conversacionId;
    }

    @GetMapping("/mensajes/ajax")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> obtenerMensajesAjax(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @RequestParam("conversacion_id") Integer conversacionId) {
        ChatService.VistaChat vista = chatService.cargarChat(yo.getId(), yo.getRol(), conversacionId);
        var fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm");
        var lista = vista.mensajes().stream().map(m -> {
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("id", m.getId());
            item.put("mio", m.getEmisor() != null && yo.getId().equals(m.getEmisor().getId()));
            item.put("contenido", m.getContenido());
            item.put("hora", m.getCreadoEn() != null ? m.getCreadoEn().format(fmt) : "");
            item.put("leido", m.isLeido());
            return item;
        }).toList();
        return java.util.Map.of("ok", true, "mensajes", lista);
    }
}
