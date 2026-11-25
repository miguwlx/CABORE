package co.cabore.CaboreApplication.controller;

import co.cabore.CaboreApplication.model.Pedido;
import co.cabore.CaboreApplication.service.FacturaService;
import co.cabore.CaboreApplication.service.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Gestión e historial de pedidos del cliente autenticado, incluida su factura.
 * Las consultas filtran SIEMPRE por el usuario de la sesión: con el id de otro
 * pedido se responde "no encontrado" (nunca se exponen datos ajenos).
 */
@Controller
@RequiredArgsConstructor
public class PedidosController extends BaseClienteController {

    private final PedidoService pedidos;
    private final FacturaService facturas;

    @GetMapping("/cliente/pedidos")
    public String historial(@org.springframework.web.bind.annotation.RequestParam(name = "estado", defaultValue = "") String estado,
                            @org.springframework.web.bind.annotation.RequestParam(name = "q", defaultValue = "") String q,
                            Model model) {
        Integer uid = usuarioId();
        var todos = pedidos.historial(uid);
        var filtrados = pedidos.historialFiltrado(uid, estado, q);
        model.addAttribute("pedidos", filtrados);
        model.addAttribute("resumenPedidos", PedidoService.calcularResumen(todos));
        model.addAttribute("estadoFiltro", estado == null ? "" : estado.trim().toLowerCase());
        model.addAttribute("qFiltro", q == null ? "" : q.trim());
        return "pedidos";
    }

    @PostMapping("/cliente/pedido/{id}/reordenar")
    public String reordenar(@PathVariable Integer id, RedirectAttributes flash) {
        int agregados = pedidos.reordenar(id, usuarioId());
        flash.addFlashAttribute("flashOk",
                "Se agregaron " + agregados + (agregados == 1 ? " producto" : " productos")
                        + " del pedido #" + id + " a tu carrito.");
        return "redirect:/cliente/carrito";
    }

    @GetMapping("/cliente/pedido/{id}")
    public String detalle(@PathVariable Integer id, Model model) {
        model.addAttribute("pedido", pedidos.detalle(id, usuarioId()));
        return "pedido-detalle";
    }

    @PostMapping("/cliente/pedido/{id}/cancelar")
    public String cancelar(@PathVariable Integer id, RedirectAttributes flash) {
        Pedido p = pedidos.cancelar(id, usuarioId());
        flash.addFlashAttribute("flashOk",
                "Pedido #" + p.getId() + " cancelado. El stock de los productos fue restaurado.");
        return "redirect:/cliente/pedido/" + id;
    }

    /** Factura imprimible del pedido propio (numeración legacy FAC-000123). */
    @GetMapping("/cliente/factura/{id}")
    public String factura(@PathVariable Integer id, Model model) {
        model.addAttribute("pedido", facturas.factura(id, usuarioId()));
        return "factura";
    }
}
