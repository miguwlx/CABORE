package co.cabore.CaboreApplication.controller;

import co.cabore.CaboreApplication.model.Producto;
import co.cabore.CaboreApplication.model.Tienda;
import co.cabore.CaboreApplication.repository.ProductoRepository;
import co.cabore.CaboreApplication.repository.TiendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ClienteController {

    private final TiendaRepository tiendas;
    private final ProductoRepository productos;

    @GetMapping("/cliente")
    public String catalogo(Model model) {
        List<Tienda> tiendasActivas = tiendas.findByActivoTrue();
        model.addAttribute("tiendas", tiendasActivas);
        return "cliente";
    }

    @GetMapping("/tienda/{id}")
    public String verTienda(@PathVariable Integer id, Model model) {
        Tienda tienda = tiendas.findById(id).orElseThrow();
        List<Producto> productosDeTienda = productos.findByTiendaId(id);
        model.addAttribute("tienda", tienda);
        model.addAttribute("productos", productosDeTienda);
        return "tienda-detalle";
    }
} 