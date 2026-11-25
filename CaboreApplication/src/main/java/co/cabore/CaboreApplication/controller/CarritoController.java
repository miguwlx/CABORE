package co.cabore.CaboreApplication.controller;

import co.cabore.CaboreApplication.service.CarritoService;
import co.cabore.CaboreApplication.service.CheckoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carrito de compras persistente del cliente autenticado.
 *
 * Dos formas de uso sobre el mismo servicio (fuente única de verdad en BD):
 *  - Formularios clásicos (GET/POST) para la página /cliente/carrito y los
 *    botones "Agregar" sin JavaScript.
 *  - POST /cliente/carrito/ajax con {accion, id}, equivalente al antiguo
 *    carrito_ajax.php, usado por el drawer lateral y las tarjetas del catálogo.
 */
@Controller
@RequestMapping("/cliente/carrito")
@RequiredArgsConstructor
public class CarritoController extends BaseClienteController {

    private final CarritoService carrito;

    /* ── Página completa del carrito ────────────────────────────────────── */

    @GetMapping
    public String ver(Model model) {
        CarritoService.ResumenCarrito resumen = carrito.ver(usuarioId());
        model.addAttribute("resumen", resumen);
        model.addAttribute("items", resumen.items());
        return "carrito";
    }

    /* ── Acciones por formulario (funcionan sin JS) ─────────────────────── */

    @PostMapping("/agregar")
    public String agregar(@RequestParam("productoId") Long productoId,
                          @RequestParam(name = "cantidad", defaultValue = "1") int cantidad,
                          @RequestParam(name = "volver", required = false) String volver,
                          RedirectAttributes flash) {
        try {
            carrito.agregar(usuarioId(), productoId, Math.min(Math.max(cantidad, 1), 99));
            flash.addFlashAttribute("flashOk", "Producto agregado a tu carrito.");
        } catch (CheckoutException e) {
            flash.addFlashAttribute("flashErr", e.getMessage());
        }
        return "redirect:" + volverSeguro(volver, "/cliente");
    }

    @PostMapping("/ajustar")
    public String ajustar(@RequestParam("productoId") Long productoId,
                          @RequestParam("cantidad") int cantidad,
                          RedirectAttributes flash) {
        carrito.ajustar(usuarioId(), productoId, cantidad);
        flash.addFlashAttribute("flashOk", "Cantidad actualizada.");
        return "redirect:/cliente/carrito";
    }

    @PostMapping("/eliminar")
    public String eliminar(@RequestParam("productoId") Long productoId,
                           RedirectAttributes flash) {
        carrito.eliminar(usuarioId(), productoId);
        flash.addFlashAttribute("flashOk", "Producto eliminado del carrito.");
        return "redirect:/cliente/carrito";
    }

    @PostMapping("/vaciar")
    public String vaciar(RedirectAttributes flash) {
        carrito.vaciar(usuarioId());
        flash.addFlashAttribute("flashOk", "Tu carrito quedó vacío.");
        return "redirect:/cliente/carrito";
    }

    /* ── API JSON del drawer (misma contratos que el JS heredado) ───────── */

    @PostMapping("/ajax")
    @ResponseBody
    public Map<String, Object> ajax(@RequestParam("accion") String accion,
                                    @RequestParam(name = "id", required = false) Long productoId) {
        Integer uid = usuarioId();
        try {
            switch (accion == null ? "listar" : accion) {
                case "agregar" -> {
                    exigirId(productoId);
                    carrito.agregar(uid, productoId, 1);
                }
                case "eliminar" -> {
                    exigirId(productoId);
                    carrito.eliminar(uid, productoId);
                }
                case "incrementar" -> {
                    exigirId(productoId);
                    CarritoService.ItemCarrito item = itemDe(uid, productoId);
                    carrito.ajustar(uid, productoId, (item == null ? 0 : item.cantidad()) + 1);
                }
                case "disminuir" -> {
                    exigirId(productoId);
                    CarritoService.ItemCarrito item = itemDe(uid, productoId);
                    carrito.ajustar(uid, productoId, (item == null ? 1 : item.cantidad()) - 1);
                }
                default -> { /* listar: no modifica nada */ }
            }
            CarritoService.ResumenCarrito r = carrito.ver(uid);
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("ok", true);
            ok.put("items", List.copyOf(r.items()));
            ok.put("count", r.count());
            ok.put("total", r.total());
            ok.put("cambio", r.ajustado());
            return ok;
        } catch (RuntimeException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage() == null ? "No fue posible actualizar el carrito." : e.getMessage());
            return err;
        }
    }

    @GetMapping("/resumen")
    @ResponseBody
    public CarritoService.ResumenCarrito resumen() {
        return carrito.ver(usuarioId());
    }

    private CarritoService.ItemCarrito itemDe(Integer uid, Long productoId) {
        return carrito.ver(uid).items().stream()
                .filter(i -> i.productoId().equals(productoId))
                .findFirst().orElse(null);
    }

    private void exigirId(Long productoId) {
        if (productoId == null || productoId <= 0) {
            throw new CheckoutException("Producto inválido.");
        }
    }
}
