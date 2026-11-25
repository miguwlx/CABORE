package co.cabore.CaboreApplication.controller;

import co.cabore.CaboreApplication.config.UsuarioPrincipal;
import co.cabore.CaboreApplication.model.EstadoVerificacion;
import co.cabore.CaboreApplication.model.Pedido;
import co.cabore.CaboreApplication.model.PedidoEstado;
import co.cabore.CaboreApplication.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador del Módulo Administrador (FASE 2 — Diana).
 *
 * Implementa todas las funcionalidades de admin.php, verificar_producto.php,
 * gestionar_usuario.php, gestionar_categoria.php, gestionar_tienda.php,
 * reporte_csv.php y factura.php (modo administrador).
 *
 * Soporta tanto peticiones AJAX JSON (usadas por los modales y botones rápidos
 * de admin.html) como envíos de formulario POST tradicionales con redirección.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /* ── Panel principal con todas las pestañas ─────────────────────────── */

    @GetMapping
    public String panel(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @RequestParam(name = "tab", defaultValue = "resumen") String tab,
            @RequestParam(name = "vtab", defaultValue = "pendiente") String vtab,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "estado", defaultValue = "") String estado,
            @RequestParam(name = "tienda_id", defaultValue = "0") Integer tiendaId,
            @RequestParam(name = "f_id", defaultValue = "") String fId,
            @RequestParam(name = "f_cliente", defaultValue = "") String fCliente,
            @RequestParam(name = "f_estado", defaultValue = "") String fEstado,
            @RequestParam(name = "rol", defaultValue = "") String rolFiltro,
            @RequestParam(name = "u_buscar", defaultValue = "") String uBuscar,
            @RequestParam(name = "t_buscar", defaultValue = "") String tBuscar,
            Model model) {

        AdminService.ResumenAdmin resumen = adminService.cargarResumen();
        EstadoVerificacion estadoVerif = AdminService.normalizarVtab(vtab);
        AdminService.ReporteAdminVista reporte = adminService.generarReporte(desde, hasta, estado, tiendaId);

        model.addAttribute("adminId", yo.getId());
        model.addAttribute("nombreAdmin", yo.getNombre());
        model.addAttribute("tabActiva", normalizarTab(tab));
        model.addAttribute("resumen", resumen);
        model.addAttribute("desglose", adminService.cargarDesglosePlataforma());

        // Verificación de productos
        model.addAttribute("vtab", estadoVerif.name());
        model.addAttribute("productosVerif", adminService.listarProductosVerificacion(estadoVerif));
        model.addAttribute("categoriasMap", adminService.mapaNombresCategorias());

        // Reportes
        model.addAttribute("reporte", reporte);
        model.addAttribute("estadosPedido", List.of(PedidoEstado.values()));
        model.addAttribute("tiendasList", adminService.listarTiendasOrdenadasPorNombre());

        // Facturas
        model.addAttribute("fId", fId);
        model.addAttribute("fCliente", fCliente);
        model.addAttribute("fEstado", fEstado);
        model.addAttribute("facturasList", adminService.buscarFacturas(fId, fCliente, fEstado));

        // Usuarios
        model.addAttribute("rolFiltro", rolFiltro);
        model.addAttribute("uBuscar", uBuscar);
        model.addAttribute("usuariosList", adminService.listarUsuarios(rolFiltro, uBuscar));

        // Categorías
        model.addAttribute("categoriasList", adminService.listarCategorias());

        // Tiendas
        model.addAttribute("tBuscar", tBuscar);
        model.addAttribute("tiendasGestion", adminService.listarTiendasGestion(tBuscar));

        return "admin";
    }

    /* ── Verificación de productos (verificar_producto.php) ─────────────── */

    @PostMapping("/productos/verificar/ajax")
    @ResponseBody
    public Map<String, Object> verificarProductoAjax(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @RequestParam(name = "producto_id", required = false) Long productoId,
            @RequestParam(name = "accion", required = false) String accion,
            @RequestParam(name = "motivo", required = false) String motivo) {
        try {
            EstadoVerificacion nuevo = adminService.verificarProducto(yo.getId(), productoId, accion, motivo);
            return Map.of("ok", true, "estado", nuevo.name());
        } catch (RuntimeException e) {
            return errorJson(e, "No se pudo actualizar el producto.");
        }
    }

    @PostMapping("/productos/verificar")
    public String verificarProductoForm(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @RequestParam("producto_id") Long productoId,
            @RequestParam("accion") String accion,
            @RequestParam(name = "motivo", required = false) String motivo,
            @RequestParam(name = "vtab", defaultValue = "pendiente") String vtab,
            RedirectAttributes flash) {
        try {
            EstadoVerificacion nuevo = adminService.verificarProducto(yo.getId(), productoId, accion, motivo);
            flash.addFlashAttribute("flashOk",
                    nuevo == EstadoVerificacion.aprobado ? "Producto aprobado." : "Producto rechazado.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("flashErr", e.getMessage());
        }
        return "redirect:/admin?tab=verificacion&vtab=" + vtab;
    }

    /* ── Gestión de usuarios (gestionar_usuario.php) ────────────────────── */

    @PostMapping("/usuarios/estado/ajax")
    @ResponseBody
    public Map<String, Object> cambiarEstadoUsuarioAjax(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @RequestParam(name = "usuario_id", required = false) Integer usuarioId,
            @RequestParam(name = "activo", required = false) Integer activo) {
        try {
            boolean nuevo = adminService.cambiarEstadoUsuario(yo.getId(), usuarioId, activo);
            return Map.of("ok", true, "activo", nuevo ? 1 : 0);
        } catch (RuntimeException e) {
            return errorJson(e, "No se pudo actualizar el usuario.");
        }
    }

    @PostMapping("/usuarios/estado")
    public String cambiarEstadoUsuarioForm(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @RequestParam("usuario_id") Integer usuarioId,
            @RequestParam("activo") Integer activo,
            RedirectAttributes flash) {
        try {
            boolean nuevo = adminService.cambiarEstadoUsuario(yo.getId(), usuarioId, activo);
            flash.addFlashAttribute("flashOk", nuevo ? "Usuario reactivado." : "Usuario suspendido.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("flashErr", e.getMessage());
        }
        return "redirect:/admin?tab=usuarios";
    }

    /* ── Gestión de categorías (gestionar_categoria.php) ────────────────── */

    @PostMapping("/categorias/ajax")
    @ResponseBody
    public Map<String, Object> gestionarCategoriaAjax(
            @RequestParam(name = "accion", required = false) String accion,
            @RequestParam(name = "id", required = false) Integer id,
            @RequestParam(name = "nombre", required = false) String nombre,
            @RequestParam(name = "icono", required = false) String icono) {
        try {
            if ("guardar".equals(accion)) {
                adminService.guardarCategoria(id, nombre, icono);
                return Map.of("ok", true);
            }
            if ("eliminar".equals(accion)) {
                adminService.eliminarCategoria(id);
                return Map.of("ok", true);
            }
            return Map.of("ok", false, "error", "Acción no reconocida.");
        } catch (RuntimeException e) {
            return errorJson(e, "No se pudo procesar la categoría.");
        }
    }

    @PostMapping("/categorias/guardar")
    public String guardarCategoriaForm(
            @RequestParam(name = "id", required = false) Integer id,
            @RequestParam("nombre") String nombre,
            @RequestParam(name = "icono", required = false) String icono,
            RedirectAttributes flash) {
        try {
            adminService.guardarCategoria(id, nombre, icono);
            flash.addFlashAttribute("flashOk", "Categoría guardada correctamente.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("flashErr", e.getMessage());
        }
        return "redirect:/admin?tab=categorias";
    }

    @PostMapping("/categorias/eliminar")
    public String eliminarCategoriaForm(
            @RequestParam("id") Integer id,
            RedirectAttributes flash) {
        try {
            adminService.eliminarCategoria(id);
            flash.addFlashAttribute("flashOk", "Categoría eliminada.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("flashErr", e.getMessage());
        }
        return "redirect:/admin?tab=categorias";
    }

    /* ── Gestión de tiendas (gestionar_tienda.php) ──────────────────────── */

    @PostMapping("/tiendas/estado/ajax")
    @ResponseBody
    public Map<String, Object> cambiarEstadoTiendaAjax(
            @RequestParam(name = "tienda_id", required = false) Integer tiendaId,
            @RequestParam(name = "activo", required = false) Integer activo,
            @RequestParam(name = "motivo", required = false) String motivo) {
        try {
            boolean nuevo = adminService.cambiarEstadoTienda(tiendaId, activo, motivo);
            return Map.of("ok", true, "activo", nuevo ? 1 : 0);
        } catch (RuntimeException e) {
            return errorJson(e, "No se pudo actualizar la tienda.");
        }
    }

    @PostMapping("/tiendas/estado")
    public String cambiarEstadoTiendaForm(
            @RequestParam("tienda_id") Integer tiendaId,
            @RequestParam("activo") Integer activo,
            @RequestParam(name = "motivo", required = false) String motivo,
            RedirectAttributes flash) {
        try {
            boolean nuevo = adminService.cambiarEstadoTienda(tiendaId, activo, motivo);
            flash.addFlashAttribute("flashOk", nuevo ? "Tienda reactivada." : "Tienda suspendida.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("flashErr", e.getMessage());
        }
        return "redirect:/admin?tab=tiendas";
    }

    /* ── Exportación de reporte CSV (reporte_csv.php) ───────────────────── */

    @GetMapping("/reportes/csv")
    public ResponseEntity<byte[]> exportarCsv(
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "estado", defaultValue = "") String estado,
            @RequestParam(name = "tienda_id", defaultValue = "0") Integer tiendaId) {

        byte[] bytes = adminService.exportarCsv(desde, hasta, estado, tiendaId);
        String filename = "reporte_ventas_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))
                + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(bytes);
    }

    /* ── Gestión de estado de pedidos desde administrador ───────────────── */

    @PostMapping("/pedidos/estado/ajax")
    @ResponseBody
    public Map<String, Object> cambiarEstadoPedidoAdminAjax(
            @RequestParam(name = "pedido_id", required = false) Integer pedidoId,
            @RequestParam(name = "estado", required = false) String estado) {
        try {
            PedidoEstado nuevo = adminService.cambiarEstadoPedidoAdmin(pedidoId, estado);
            return Map.of("ok", true, "estado", nuevo.name());
        } catch (RuntimeException e) {
            return errorJson(e, "No se pudo actualizar el pedido.");
        }
    }

    @PostMapping("/pedidos/{id}/estado")
    public String cambiarEstadoPedidoAdminForm(
            @PathVariable Integer id,
            @RequestParam("estado") String estado,
            RedirectAttributes flash) {
        try {
            adminService.cambiarEstadoPedidoAdmin(id, estado);
            flash.addFlashAttribute("flashOk", "Estado del pedido #" + id + " actualizado.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("flashErr", e.getMessage());
        }
        return "redirect:/admin?tab=facturas";
    }

    /* ── Factura imprimible vista por administrador (factura.php) ───────── */

    @GetMapping("/factura/{id}")
    public String verFactura(@PathVariable Integer id, Model model, RedirectAttributes flash) {
        try {
            Pedido pedido = adminService.obtenerFacturaAdmin(id);
            model.addAttribute("pedido", pedido);
            model.addAttribute("volverUrl", "/admin?tab=facturas");
            model.addAttribute("volverTexto", "← Volver a facturas");
            return "factura";
        } catch (RuntimeException e) {
            flash.addFlashAttribute("flashErr", e.getMessage());
            return "redirect:/admin?tab=facturas";
        }
    }

    private static String normalizarTab(String tab) {
        if (tab == null) return "resumen";
        return switch (tab.trim().toLowerCase()) {
            case "verificacion", "reportes", "facturas", "usuarios", "categorias", "tiendas" -> tab.trim().toLowerCase();
            default -> "resumen";
        };
    }

    private static Map<String, Object> errorJson(RuntimeException e, String porDefecto) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("ok", false);
        err.put("error", e.getMessage() == null || e.getMessage().isBlank() ? porDefecto : e.getMessage());
        return err;
    }
}
