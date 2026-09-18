package co.cabore.CaboreApplication.controller;

import co.cabore.CaboreApplication.config.UsuarioPrincipal;
import co.cabore.CaboreApplication.model.Producto;
import co.cabore.CaboreApplication.model.Tienda;
import co.cabore.CaboreApplication.repository.ProductoRepository;
import co.cabore.CaboreApplication.repository.TiendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/emprendedor")
@RequiredArgsConstructor
public class EmprendedorController {

    private final TiendaRepository tiendas;
    private final ProductoRepository productos;

    // ── Panel: lista de productos + formulario para crear ──
    @GetMapping
    public String panel(@AuthenticationPrincipal UsuarioPrincipal yo, Model model) {
        Tienda tienda = miTienda(yo);
        List<Producto> lista = productos.findByTiendaId(tienda.getId());

        long sinStock = lista.stream()
                .filter(p -> p.getStock() == null || p.getStock() <= 0)
                .count();

        model.addAttribute("nombreUsuario", yo.getNombre());
        model.addAttribute("tienda", tienda);
        model.addAttribute("productos", lista);
        model.addAttribute("totalProductos", lista.size());
        model.addAttribute("sinStock", sinStock);
        return "emprendedor";
    }

    // ── Crear ──
    @PostMapping("/productos")
    public String crear(@AuthenticationPrincipal UsuarioPrincipal yo,
                        @RequestParam String nombre,
                        @RequestParam(required = false) String descripcion,
                        @RequestParam Double precio,
                        @RequestParam Integer stock,
                        RedirectAttributes flash) {
        Tienda tienda = miTienda(yo);

        String error = validar(nombre, precio, stock);
        if (error != null) {
            flash.addFlashAttribute("error", error);
            return "redirect:/emprendedor";
        }

        productos.save(new Producto(nombre.trim(), limpiar(descripcion), precio, stock, tienda));
        flash.addFlashAttribute("ok", "Producto publicado.");
        return "redirect:/emprendedor";
    }

    // ── Editar (formulario) ──
    @GetMapping("/productos/{id}/editar")
    public String editar(@AuthenticationPrincipal UsuarioPrincipal yo,
                         @PathVariable Long id,
                         Model model) {
        Producto p = productoPropio(id, miTienda(yo));
        model.addAttribute("producto", p);
        return "editar-producto";
    }

    // ── Actualizar ──
    @PostMapping("/productos/{id}")
    public String actualizar(@AuthenticationPrincipal UsuarioPrincipal yo,
                             @PathVariable Long id,
                             @RequestParam String nombre,
                             @RequestParam(required = false) String descripcion,
                             @RequestParam Double precio,
                             @RequestParam Integer stock,
                             RedirectAttributes flash) {
        Producto p = productoPropio(id, miTienda(yo));

        String error = validar(nombre, precio, stock);
        if (error != null) {
            flash.addFlashAttribute("error", error);
            return "redirect:/emprendedor/productos/" + id + "/editar";
        }

        p.setNombre(nombre.trim());
        p.setDescripcion(limpiar(descripcion));
        p.setPrecio(precio);
        p.setStock(stock);
        productos.save(p);

        flash.addFlashAttribute("ok", "Cambios guardados.");
        return "redirect:/emprendedor";
    }

    // ── Eliminar ──
    @PostMapping("/productos/{id}/eliminar")
    public String eliminar(@AuthenticationPrincipal UsuarioPrincipal yo,
                           @PathVariable Long id,
                           RedirectAttributes flash) {
        Producto p = productoPropio(id, miTienda(yo));
        try {
            productos.delete(p);
            flash.addFlashAttribute("ok", "Producto eliminado.");
        } catch (DataIntegrityViolationException e) {
            flash.addFlashAttribute("error",
                    "No se puede eliminar: el producto ya está asociado a pedidos u otros registros.");
        }
        return "redirect:/emprendedor";
    }

    // ── Helpers ──

    /** La tienda del emprendedor que inició sesión. */
    private Tienda miTienda(UsuarioPrincipal yo) {
        return tiendas.findByUsuarioId(yo.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Tu cuenta no tiene una tienda asociada."));
    }

    /** Devuelve el producto solo si pertenece a la tienda indicada. */
    private Producto productoPropio(Long id, Tienda tienda) {
        Producto p = productos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (p.getTienda() == null || !p.getTienda().getId().equals(tienda.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return p;
    }

    private String validar(String nombre, Double precio, Integer stock) {
        if (nombre == null || nombre.trim().length() < 2) {
            return "El nombre debe tener al menos 2 caracteres.";
        }
        if (precio == null || precio <= 0) {
            return "El precio debe ser mayor a 0.";
        }
        if (stock == null || stock < 0) {
            return "El stock no puede ser negativo.";
        }
        return null;
    }

    private String limpiar(String texto) {
        return (texto == null || texto.isBlank()) ? null : texto.trim();
    }
}