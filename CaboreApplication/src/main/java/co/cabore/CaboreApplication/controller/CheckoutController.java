package co.cabore.CaboreApplication.controller;

import co.cabore.CaboreApplication.model.Pedido;
import co.cabore.CaboreApplication.model.Usuario;
import co.cabore.CaboreApplication.service.CarritoService;
import co.cabore.CaboreApplication.service.CheckoutException;
import co.cabore.CaboreApplication.service.CheckoutService;
import co.cabore.CaboreApplication.service.PerfilService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Checkout del cliente: muestra el resumen server-side (precios leídos de la
 * BD) y confirma la COMPRA REAL: persiste pedido + ítems, descuenta stock y
 * limpia el carrito en una sola transacción. Al finalizar redirige al detalle
 * del pedido recién creado, donde el cliente ve su confirmación y su factura.
 */
@Controller
@RequestMapping("/cliente/checkout")
@RequiredArgsConstructor
public class CheckoutController extends BaseClienteController {

    private final CarritoService carrito;
    private final CheckoutService checkout;
    private final PerfilService perfil;

    @GetMapping
    public String formulario(Model model, RedirectAttributes flash) {
        CarritoService.ResumenCarrito resumen = carrito.ver(usuarioId());
        if (resumen.items().isEmpty()) {
            flash.addFlashAttribute("flashErr", "Tu carrito está vacío: agrega productos antes de comprar.");
            return "redirect:/cliente";
        }
        Usuario u = perfil.usuario(usuarioId());
        model.addAttribute("resumen", resumen);
        model.addAttribute("items", resumen.items());
        model.addAttribute("direccionUsuario", u.getDireccion());
        model.addAttribute("telefonoUsuario", u.getTelefono());
        return "checkout";
    }

    @PostMapping
    public String confirmar(@RequestParam("direccion") String direccion,
                            @RequestParam(name = "telefono", required = false) String telefono,
                            @RequestParam(name = "notas", required = false) String notas,
                            @RequestParam(name = "guardar_datos", required = false) String guardarDatos,
                            Model model,
                            RedirectAttributes flash) {
        try {
            Pedido pedido = checkout.comprar(usuarioId(), direccion, telefono, notas);
            if (guardarDatos != null) {
                checkout.recordarDatosEntrega(usuarioId(), direccion, telefono);
            }
            flash.addFlashAttribute("flashOk",
                    "¡Pedido #" + pedido.getId() + " realizado con éxito! Te contactaremos para confirmar la entrega.");
            return "redirect:/cliente/pedido/" + pedido.getId() + "?nuevo=1";
        } catch (CheckoutException e) {
            // Se recarga el formulario con el error y el carrito intacto.
            CarritoService.ResumenCarrito resumen = carrito.ver(usuarioId());
            model.addAttribute("error", e.getMessage());
            model.addAttribute("resumen", resumen);
            model.addAttribute("items", resumen.items());
            model.addAttribute("direccionUsuario", direccion);
            model.addAttribute("telefonoUsuario", telefono);
            return "checkout";
        }
    }
}
