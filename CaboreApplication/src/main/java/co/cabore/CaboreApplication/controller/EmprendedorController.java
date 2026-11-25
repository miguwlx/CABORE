package co.cabore.CaboreApplication.controller;

import co.cabore.CaboreApplication.config.UsuarioPrincipal;
import co.cabore.CaboreApplication.model.*;
import co.cabore.CaboreApplication.service.EmprendedorService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador completo del Módulo Emprendedor (FASE 3 — Miguel).
 *
 * Migra todas las secciones y acciones de emprendedor.php, guardar_producto.php,
 * editar_producto.php, actualizar_producto.php, eliminar_producto.php,
 * actualizar_tienda.php, actualizar_pedido.php y factura.php (modo emprendedor).
 */
@Controller
@RequestMapping("/emprendedor")
@RequiredArgsConstructor
public class EmprendedorController {

    private final EmprendedorService emprendedorService;

    /* ── Panel principal con todas las secciones ────────────────────────── */

    @GetMapping
    public String panel(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @RequestParam(name = "tab", defaultValue = "inicio") String tab,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "estado", defaultValue = "") String estado,
            @RequestParam(name = "f_id", defaultValue = "") String fId,
            Model model) {

        Tienda tienda = emprendedorService.obtenerOCrearTienda(yo.getId());
        List<Producto> listaProductos = emprendedorService.listarProductosTienda(tienda.getId());

        long sinStock = listaProductos.stream()
                .filter(p -> p.getStock() == null || p.getStock() <= 0)
                .count();
        long stockBajo = listaProductos.stream()
                .filter(p -> p.getStock() != null && p.getStock() > 0 && p.getStock() <= 5)
                .count();
        long pendientesVerif = listaProductos.stream()
                .filter(p -> p.getEstadoVerificacion() == EstadoVerificacion.pendiente)
                .count();
        long totalActivos = listaProductos.stream().filter(Producto::isActivo).count();
        long totalDestacados = listaProductos.stream().filter(Producto::isDestacado).count();
        BigDecimal valorInventario = EmprendedorService.calcularValorInventario(listaProductos);

        EmprendedorService.ResumenVentasTienda ventas = emprendedorService.cargarVentasTienda(tienda.getId());
        EmprendedorService.ReporteTiendaVista reporte = emprendedorService.generarReporteTienda(
                tienda.getId(), desde, hasta, estado);

        model.addAttribute("nombreUsuario", yo.getNombre());
        model.addAttribute("tabActiva", normalizarTab(tab));
        model.addAttribute("tienda", tienda);
        model.addAttribute("plantillasOpciones", Tienda.PLANTILLAS);
        model.addAttribute("totalMensajesNoLeidos",
                emprendedorService.contarMensajesNoLeidos(tienda.getId(), yo.getId()));

        // Catálogo e inventario
        model.addAttribute("productos", listaProductos);
        model.addAttribute("ultimosProductos",
                listaProductos.size() > 5 ? listaProductos.subList(0, 5) : listaProductos);
        model.addAttribute("totalProductos", listaProductos.size());
        model.addAttribute("totalActivos", totalActivos);
        model.addAttribute("totalDestacados", totalDestacados);
        model.addAttribute("sinStock", sinStock);
        model.addAttribute("stockBajo", stockBajo);
        model.addAttribute("pendientesVerif", pendientesVerif);
        model.addAttribute("valorInventario", valorInventario);
        model.addAttribute("categorias", emprendedorService.listarCategorias());
        model.addAttribute("categoriasMap", emprendedorService.mapaCategorias());

        // Ventas y pedidos recibidos
        model.addAttribute("ventas", ventas);
        model.addAttribute("estadosPedido", List.of(PedidoEstado.values()));

        // Reportes
        model.addAttribute("reporte", reporte);

        // Facturas de la tienda
        model.addAttribute("fId", fId);
        model.addAttribute("facturasTienda", emprendedorService.buscarFacturasTienda(tienda.getId(), fId));

        return "emprendedor";
    }

    /* ── Crear producto (guardar_producto.php) ──────────────────────────── */

    @PostMapping("/productos")
    public String crear(@AuthenticationPrincipal UsuarioPrincipal yo,
                        @RequestParam String nombre,
                        @RequestParam(required = false) String descripcion,
                        @RequestParam BigDecimal precio,
                        @RequestParam(name = "precio_oferta", required = false) BigDecimal precioOferta,
                        @RequestParam(name = "categoria_id", required = false) Integer categoriaId,
                        @RequestParam(defaultValue = "0") Integer stock,
                        @RequestParam(name = "destacado", required = false) Boolean destacado,
                        @RequestParam(name = "activo", required = false) Boolean activo,
                        @RequestParam(name = "formCompleto", required = false) String formCompleto,
                        @RequestParam(name = "imagen", required = false) MultipartFile imagen,
                        RedirectAttributes flash) {
        Tienda tienda = emprendedorService.obtenerOCrearTienda(yo.getId());
        boolean esDestacado = Boolean.TRUE.equals(destacado);
        // Si viene del formulario completo con checkbox "activo", se respeta si fue desmarcado;
        // si es un POST simple sin formCompleto, activo = true por defecto.
        boolean esActivo = (formCompleto != null) ? Boolean.TRUE.equals(activo) : (activo == null || activo);

        try {
            Producto creado = emprendedorService.crearProducto(
                    tienda, nombre, descripcion, precio, precioOferta,
                    categoriaId, stock, esDestacado, esActivo, imagen);
            flash.addFlashAttribute("ok",
                    "Producto «" + creado.getNombre() + "» guardado correctamente. Quedará visible para los clientes en cuanto un administrador lo revise y apruebe.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/emprendedor?tab=productos";
    }

    /* ── Editar producto (editar_producto.php) ──────────────────────────── */

    @GetMapping("/productos/{id}/editar")
    public String editar(@AuthenticationPrincipal UsuarioPrincipal yo,
                         @PathVariable Long id,
                         Model model) {
        Tienda tienda = emprendedorService.exigirTienda(yo.getId());
        Producto p = emprendedorService.obtenerProductoPropio(id, tienda);
        model.addAttribute("producto", p);
        model.addAttribute("categorias", emprendedorService.listarCategorias());
        return "editar-producto";
    }

    /* ── Actualizar producto (actualizar_producto.php) ──────────────────── */

    @PostMapping("/productos/{id}")
    public String actualizar(@AuthenticationPrincipal UsuarioPrincipal yo,
                             @PathVariable Long id,
                             @RequestParam String nombre,
                             @RequestParam(required = false) String descripcion,
                             @RequestParam BigDecimal precio,
                             @RequestParam(name = "precio_oferta", required = false) BigDecimal precioOferta,
                             @RequestParam(name = "categoria_id", required = false) Integer categoriaId,
                             @RequestParam Integer stock,
                             @RequestParam(name = "destacado", required = false) Boolean destacado,
                             @RequestParam(name = "activo", required = false) Boolean activo,
                             @RequestParam(name = "formCompleto", required = false) String formCompleto,
                             @RequestParam(name = "imagen", required = false) MultipartFile imagen,
                             RedirectAttributes flash) {
        Tienda tienda = emprendedorService.exigirTienda(yo.getId());
        Boolean nuevoDestacado = formCompleto != null ? Boolean.TRUE.equals(destacado) : destacado;
        Boolean nuevoActivo = formCompleto != null ? Boolean.TRUE.equals(activo) : activo;

        try {
            emprendedorService.actualizarProducto(
                    tienda, id, nombre, descripcion, precio, precioOferta,
                    categoriaId, stock, nuevoDestacado, nuevoActivo, imagen);
            flash.addFlashAttribute("ok", "Cambios guardados.");
            return "redirect:/emprendedor?tab=productos";
        } catch ( co.cabore.CaboreApplication.service.OperacionInvalidaException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/emprendedor/productos/" + id + "/editar";
        }
    }

    /* ── Eliminar producto (eliminar_producto.php con protección de pedidos) ── */

    @PostMapping("/productos/{id}/eliminar")
    public String eliminar(@AuthenticationPrincipal UsuarioPrincipal yo,
                           @PathVariable Long id,
                           RedirectAttributes flash) {
        Tienda tienda = emprendedorService.exigirTienda(yo.getId());
        try {
            switch (emprendedorService.eliminarProducto(tienda, id)) {
                case ELIMINADO -> flash.addFlashAttribute("ok", "Producto eliminado.");
                case ARCHIVADO -> flash.addFlashAttribute("ok",
                        "El producto ya tiene pedidos: se desactivó para conservar el historial y las facturas.");
            }
        } catch (DataIntegrityViolationException e) {
            flash.addFlashAttribute("error",
                    "No se puede eliminar: el producto está asociado a otros registros.");
        }
        return "redirect:/emprendedor?tab=productos";
    }

    /* ── Acciones rápidas de inventario (stock, activo, destacado) ──────── */

    @PostMapping("/productos/stock/ajax")
    @ResponseBody
    public Map<String, Object> actualizarStockAjax(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @RequestParam(name = "producto_id", required = false) Long productoId,
            @RequestParam(name = "stock", required = false) Integer stock) {
        try {
            Tienda tienda = emprendedorService.exigirTienda(yo.getId());
            int actualizado = emprendedorService.actualizarStockRapido(tienda, productoId, stock);
            return Map.of("ok", true, "stock", actualizado);
        } catch (RuntimeException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage() == null ? "No se pudo actualizar el stock." : e.getMessage());
            return err;
        }
    }

    @PostMapping("/productos/{id}/stock")
    public String actualizarStockForm(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @PathVariable Long id,
            @RequestParam("stock") Integer stock,
            RedirectAttributes flash) {
        try {
            Tienda tienda = emprendedorService.exigirTienda(yo.getId());
            int actualizado = emprendedorService.actualizarStockRapido(tienda, id, stock);
            flash.addFlashAttribute("ok", "Stock actualizado a " + actualizado + " unidades.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/emprendedor?tab=productos";
    }

    @PostMapping("/productos/toggle/ajax")
    @ResponseBody
    public Map<String, Object> alternarCampoProductoAjax(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @RequestParam(name = "producto_id", required = false) Long productoId,
            @RequestParam(name = "campo", required = false) String campo) {
        try {
            Tienda tienda = emprendedorService.exigirTienda(yo.getId());
            Producto p = emprendedorService.alternarCampoRapido(tienda, productoId, campo);
            return Map.of("ok", true, "activo", p.isActivo(), "destacado", p.isDestacado());
        } catch (RuntimeException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage() == null ? "No se pudo actualizar el producto." : e.getMessage());
            return err;
        }
    }

    @PostMapping("/productos/{id}/toggle")
    public String alternarCampoProductoForm(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @PathVariable Long id,
            @RequestParam("campo") String campo,
            RedirectAttributes flash) {
        try {
            Tienda tienda = emprendedorService.exigirTienda(yo.getId());
            emprendedorService.alternarCampoRapido(tienda, id, campo);
            flash.addFlashAttribute("ok", "Estado del producto actualizado.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/emprendedor?tab=productos";
    }

    /* ── Actualizar tienda (actualizar_tienda.php: info, apariencia, contacto) ── */

    @PostMapping("/tienda")
    public String actualizarTienda(@AuthenticationPrincipal UsuarioPrincipal yo,
                                   @RequestParam(name = "seccion", defaultValue = "info") String seccion,
                                   @RequestParam(name = "nombre", required = false) String nombre,
                                   @RequestParam(name = "descripcion", required = false) String descripcion,
                                   @RequestParam(name = "ciudad", required = false) String ciudad,
                                   @RequestParam(name = "color", required = false) String color,
                                   @RequestParam(name = "plantilla", required = false) String plantilla,
                                   @RequestParam(name = "logo", required = false) MultipartFile logo,
                                   @RequestParam(name = "banner", required = false) MultipartFile banner,
                                   @RequestParam(name = "whatsapp", required = false) String whatsapp,
                                   @RequestParam(name = "instagram", required = false) String instagram,
                                   RedirectAttributes flash) {
        Tienda tienda = emprendedorService.obtenerOCrearTienda(yo.getId());
        try {
            switch (seccion == null ? "info" : seccion.trim().toLowerCase()) {
                case "info" -> {
                    emprendedorService.actualizarInformacionTienda(tienda, nombre, descripcion, ciudad);
                    flash.addFlashAttribute("ok", "Información de la tienda actualizada correctamente.");
                }
                case "apariencia" -> {
                    emprendedorService.actualizarAparienciaTienda(tienda, yo.getId(), color, plantilla, logo, banner);
                    flash.addFlashAttribute("ok", "Apariencia actualizada correctamente.");
                }
                case "contacto" -> {
                    emprendedorService.actualizarContactoTienda(tienda, whatsapp, instagram);
                    flash.addFlashAttribute("ok", "Datos de contacto actualizados.");
                }
                default -> flash.addFlashAttribute("error", "Sección no válida.");
            }
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/emprendedor?tab=tienda";
    }

    /* ── Cambiar estado de pedido recibido (actualizar_pedido.php) ──────── */

    @PostMapping("/pedidos/estado/ajax")
    @ResponseBody
    public Map<String, Object> cambiarEstadoPedidoAjax(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @RequestParam(name = "pedido_id", required = false) Integer pedidoId,
            @RequestParam(name = "estado", required = false) String estado) {
        try {
            Tienda tienda = emprendedorService.exigirTienda(yo.getId());
            PedidoEstado nuevo = emprendedorService.cambiarEstadoPedido(tienda, pedidoId, estado);
            return Map.of("ok", true, "estado", nuevo.name());
        } catch (RuntimeException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage() == null ? "No se pudo actualizar el pedido." : e.getMessage());
            return err;
        }
    }

    @PostMapping("/pedidos/{pedidoId}/estado")
    public String cambiarEstadoPedidoForm(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @PathVariable Integer pedidoId,
            @RequestParam("estado") String estado,
            RedirectAttributes flash) {
        try {
            Tienda tienda = emprendedorService.exigirTienda(yo.getId());
            emprendedorService.cambiarEstadoPedido(tienda, pedidoId, estado);
            flash.addFlashAttribute("ok", "Estado del pedido #" + pedidoId + " actualizado.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/emprendedor?tab=ventas";
    }

    /* ── Exportar reporte CSV de la tienda ──────────────────────────────── */

    @GetMapping("/reportes/csv")
    public ResponseEntity<byte[]> exportarCsv(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "estado", defaultValue = "") String estado) {

        Tienda tienda = emprendedorService.exigirTienda(yo.getId());
        byte[] bytes = emprendedorService.exportarCsvTienda(tienda, desde, hasta, estado);
        String filename = "reporte_tienda_" + tienda.getId() + "_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))
                + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }

    /* ── Factura de pedido filtrada a los productos de la tienda (factura.php) ── */

    @GetMapping("/factura/{id}")
    public String verFactura(@AuthenticationPrincipal UsuarioPrincipal yo,
                             @PathVariable Integer id,
                             Model model,
                             RedirectAttributes flash) {
        try {
            Tienda tienda = emprendedorService.exigirTienda(yo.getId());
            Pedido pedidoFiltrado = emprendedorService.obtenerFacturaEmprendedor(tienda, id);
            model.addAttribute("pedido", pedidoFiltrado);
            model.addAttribute("volverUrl", "/emprendedor?tab=facturas");
            model.addAttribute("volverTexto", "← Volver a mi panel");
            return "factura";
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/emprendedor?tab=facturas";
        }
    }

    private static String normalizarTab(String tab) {
        if (tab == null) return "inicio";
        return switch (tab.trim().toLowerCase()) {
            case "tienda", "ventas", "reportes", "facturas", "productos", "agregar" -> tab.trim().toLowerCase();
            default -> "inicio";
        };
    }
}
