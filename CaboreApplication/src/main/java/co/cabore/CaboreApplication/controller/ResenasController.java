package co.cabore.CaboreApplication.controller;

import co.cabore.CaboreApplication.service.ResenaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Reseñas y calificaciones dejadas por el cliente autenticado sobre productos
 * que compró en pedidos entregados. Publicar de nuevo sobre el mismo producto
 * actualiza la reseña propia; solo el dueño puede eliminarla.
 */
@Controller
@RequestMapping("/cliente/resenas")
@RequiredArgsConstructor
public class ResenasController extends BaseClienteController {

    private final ResenaService resenas;

    @PostMapping
    public String publicar(@RequestParam("productoId") Long productoId,
                           @RequestParam("calificacion") int calificacion,
                           @RequestParam(name = "comentario", required = false) String comentario,
                           RedirectAttributes flash) {
        resenas.publicar(usuarioId(), productoId, calificacion, comentario);
        flash.addFlashAttribute("flashOk", "Gracias por tu reseña. Se guardó correctamente.");
        return "redirect:/cliente/producto/" + productoId;
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Integer id,
                           @RequestParam("productoId") Long productoId,
                           RedirectAttributes flash) {
        resenas.eliminar(usuarioId(), id);
        flash.addFlashAttribute("flashOk", "Tu reseña fue eliminada.");
        return "redirect:/cliente/producto/" + productoId;
    }
}
