package co.cabore.CaboreApplication.controller;

import co.cabore.CaboreApplication.model.Favorito;
import co.cabore.CaboreApplication.model.Producto;
import co.cabore.CaboreApplication.service.CatalogoService;
import co.cabore.CaboreApplication.service.FavoritoService;
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
 * Favoritos persistentes del cliente (tabla favoritos de MariaDB).
 * El corazón de las tarjetas llama a /toggle/ajax vía fetch (JSON); además hay
 * variante por formulario para navegadores sin JavaScript.
 */
@Controller
@RequestMapping("/cliente/favoritos")
@RequiredArgsConstructor
public class FavoritosController extends BaseClienteController {

    private final FavoritoService favoritos;
    private final CatalogoService catalogo;

    @GetMapping
    public String ver(Model model) {
        List<Favorito> lista = favoritos.listar(usuarioId());
        List<Producto> prods = lista.stream().map(Favorito::getProducto).toList();
        catalogo.anotarCalificaciones(prods);
        model.addAttribute("favoritos", lista);
        model.addAttribute("productos", prods);
        return "favoritos";
    }

    @PostMapping("/toggle")
    public String toggleForm(@RequestParam("productoId") Long productoId,
                             @RequestParam(name = "volver", required = false) String volver,
                             RedirectAttributes flash) {
        boolean ahora = favoritos.alternar(usuarioId(), productoId);
        flash.addFlashAttribute("flashOk",
                ahora ? "Guardado en tus favoritos." : "Quitado de tus favoritos.");
        return "redirect:" + volverSeguro(volver, "/cliente/favoritos");
    }

    /** Variante JSON usada por los corazones del catálogo y del modal. */
    @PostMapping("/toggle/ajax")
    @ResponseBody
    public Map<String, Object> toggleAjax(@RequestParam("productoId") Long productoId) {
        Map<String, Object> res = new LinkedHashMap<>();
        try {
            Integer uid = usuarioId();
            boolean ahora = favoritos.alternar(uid, productoId);
            res.put("ok", true);
            res.put("favorito", ahora);
            res.put("count", favoritos.idsFavoritos(uid).size());
        } catch (RuntimeException e) {
            res.put("ok", false);
            res.put("error", "No fue posible actualizar favoritos.");
        }
        return res;
    }
}
