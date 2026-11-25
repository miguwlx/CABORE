package co.cabore.CaboreApplication.service;

import co.cabore.CaboreApplication.model.*;
import co.cabore.CaboreApplication.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio completo del módulo Administrador (FASE 2).
 * Migra fielmente la lógica de admin.php, verificar_producto.php,
 * gestionar_usuario.php, gestionar_categoria.php, gestionar_tienda.php,
 * reporte_csv.php y factura.php.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final DateTimeFormatter FMT_DIA_CORTO = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FMT_FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UsuarioRepository usuarios;
    private final TiendaRepository tiendas;
    private final ProductoRepository productos;
    private final CategoriaRepository categorias;
    private final PedidoRepository pedidos;
    private final PedidoItemRepository pedidoItems;

    /* ── DTOs / Records de vista ────────────────────────────────────────── */

    public record ResumenAdmin(
            long totalUsuarios,
            long totalClientes,
            long totalEmprendedores,
            long totalAdministradores,
            long totalTiendas,
            long totalProductos,
            long productosPendientes,
            long productosAprobados,
            long productosRechazados,
            long totalPedidos,
            long pedidosPendientes,
            BigDecimal totalVentas,
            List<Producto> pendientesPreview,
            List<Pedido> pedidosPreview
    ) {}

    public record DesglosePlataforma(
            long tiendasActivas,
            long tiendasSuspendidas,
            long pedidosPendientes,
            long pedidosConfirmados,
            long pedidosEnviados,
            long pedidosEntregados,
            long pedidosCancelados
    ) {}

    public record CategoriaAdminVista(
            Integer id,
            String nombre,
            String icono,
            long totalProductos
    ) {}

    public record TiendaAdminVista(
            Integer id,
            String nombre,
            boolean activo,
            String motivoSuspension,
            String duenoNombre,
            String duenoCorreo,
            long totalProductos,
            BigDecimal totalVentas
    ) {}

    public record TopTiendaVista(
            Integer id,
            String nombre,
            long pedidos,
            BigDecimal total
    ) {}

    public record TopProductoVista(
            Long id,
            String nombre,
            long cantidad,
            BigDecimal total
    ) {}

    public record DiaVentaVista(
            String fecha,
            String label,
            BigDecimal total,
            int alturaPct
    ) {}

    public record ReporteAdminVista(
            LocalDate desde,
            LocalDate hasta,
            String estado,
            int tiendaId,
            BigDecimal totalVentas,
            long totalPedidos,
            BigDecimal ticketPromedio,
            List<TopTiendaVista> topTiendas,
            List<TopProductoVista> topProductos,
            List<DiaVentaVista> ultimos14
    ) {}

    /* ── 1. Dashboard / Resumen ─────────────────────────────────────────── */

    @Transactional(readOnly = true)
    public ResumenAdmin cargarResumen() {
        long clientes = usuarios.countByRol(Rol.cliente);
        long emprendedores = usuarios.countByRol(Rol.emprendedor);
        long admins = usuarios.countByRol(Rol.administrador);
        long totalU = clientes + emprendedores + admins;

        long totalT = tiendas.count();

        long pend = productos.countByEstadoVerificacion(EstadoVerificacion.pendiente);
        long aprob = productos.countByEstadoVerificacion(EstadoVerificacion.aprobado);
        long rech = productos.countByEstadoVerificacion(EstadoVerificacion.rechazado);
        long totalP = pend + aprob + rech;

        long totalPed = pedidos.countByEstadoNot(PedidoEstado.cancelado);
        long pedPend = pedidos.countByEstado(PedidoEstado.pendiente);
        BigDecimal ventas = pedidos.sumarTotalExceptoEstado(PedidoEstado.cancelado);
        if (ventas == null) ventas = BigDecimal.ZERO;

        List<Producto> pendPreview = productos.listarPorEstadoVerificacion(
                EstadoVerificacion.pendiente, PageRequest.of(0, 5));
        List<Pedido> pedPreview = pedidos.findAllByOrderByCreadoEnDesc(PageRequest.of(0, 6));

        return new ResumenAdmin(
                totalU, clientes, emprendedores, admins,
                totalT, totalP, pend, aprob, rech,
                totalPed, pedPend, ventas,
                pendPreview, pedPreview
        );
    }

    @Transactional(readOnly = true)
    public DesglosePlataforma cargarDesglosePlataforma() {
        long tActivas = tiendas.findByActivoTrue().size();
        long tTotal = tiendas.count();
        long tSuspendidas = Math.max(0, tTotal - tActivas);
        return new DesglosePlataforma(
                tActivas,
                tSuspendidas,
                pedidos.countByEstado(PedidoEstado.pendiente),
                pedidos.countByEstado(PedidoEstado.confirmado),
                pedidos.countByEstado(PedidoEstado.enviado),
                pedidos.countByEstado(PedidoEstado.entregado),
                pedidos.countByEstado(PedidoEstado.cancelado)
        );
    }

    /* ── 2. Verificación de productos ───────────────────────────────────── */

    public static EstadoVerificacion normalizarVtab(String vtab) {
        if (vtab == null) return EstadoVerificacion.pendiente;
        return switch (vtab.trim().toLowerCase()) {
            case "aprobado" -> EstadoVerificacion.aprobado;
            case "rechazado" -> EstadoVerificacion.rechazado;
            default -> EstadoVerificacion.pendiente;
        };
    }

    @Transactional(readOnly = true)
    public List<Producto> listarProductosVerificacion(EstadoVerificacion estado) {
        return productos.listarPorEstadoVerificacion(estado, PageRequest.of(0, 150));
    }

    @Transactional(readOnly = true)
    public Map<Integer, String> mapaNombresCategorias() {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (Categoria c : categorias.findAllByOrderByIdAsc()) {
            map.put(c.getId(), c.getNombre());
        }
        return map;
    }

    @Transactional
    public EstadoVerificacion verificarProducto(Integer adminId, Long productoId, String accion, String motivo) {
        if (productoId == null || productoId <= 0 || accion == null) {
            throw new OperacionInvalidaException("Datos inválidos.");
        }
        String acc = accion.trim().toLowerCase();
        if (!acc.equals("aprobar") && !acc.equals("rechazar")) {
            throw new OperacionInvalidaException("Datos inválidos.");
        }
        String motivoLimpio = motivo == null ? "" : motivo.trim();
        if (acc.equals("rechazar") && motivoLimpio.isEmpty()) {
            throw new OperacionInvalidaException("Debes indicar un motivo de rechazo.");
        }

        Producto p = productos.findById(productoId)
                .orElseThrow(() -> new NoSuchElementException("Producto no encontrado."));

        EstadoVerificacion nuevoEstado = acc.equals("aprobar")
                ? EstadoVerificacion.aprobado
                : EstadoVerificacion.rechazado;

        p.setEstadoVerificacion(nuevoEstado);
        p.setMotivoRechazo(acc.equals("rechazar") ? motivoLimpio : null);
        p.setRevisadoPor(adminId);
        p.setRevisadoEn(LocalDateTime.now());
        productos.save(p);
        return nuevoEstado;
    }

    /* ── 3. Gestión de usuarios ─────────────────────────────────────────── */

    @Transactional(readOnly = true)
    public List<Usuario> listarUsuarios(String rolFiltro, String buscar) {
        Rol rol = null;
        if (rolFiltro != null && !rolFiltro.isBlank()) {
            try {
                rol = Rol.valueOf(rolFiltro.trim().toLowerCase());
            } catch (IllegalArgumentException ignored) {
                rol = null;
            }
        }
        String q = buscar == null ? "" : buscar.trim();
        List<Usuario> lista = usuarios.buscarAdmin(rol, q);
        return lista.size() > 200 ? lista.subList(0, 200) : lista;
    }

    @Transactional
    public boolean cambiarEstadoUsuario(Integer adminId, Integer usuarioId, Integer activoParam) {
        if (usuarioId == null || usuarioId <= 0 || activoParam == null || (activoParam != 0 && activoParam != 1)) {
            throw new OperacionInvalidaException("Datos inválidos.");
        }
        if (usuarioId.equals(adminId)) {
            throw new OperacionInvalidaException("No puedes suspender tu propia cuenta.");
        }
        Usuario u = usuarios.findById(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado."));
        if (u.getRol() == Rol.administrador) {
            throw new OperacionInvalidaException("No puedes suspender a otro administrador.");
        }
        boolean nuevoEstado = activoParam == 1;
        u.setActivo(nuevoEstado);
        usuarios.save(u);
        return nuevoEstado;
    }

    /* ── 4. Gestión de categorías ───────────────────────────────────────── */

    @Transactional(readOnly = true)
    public List<CategoriaAdminVista> listarCategorias() {
        List<Categoria> lista = categorias.findAllByOrderByNombreAsc();
        List<CategoriaAdminVista> res = new ArrayList<>(lista.size());
        for (Categoria c : lista) {
            long count = productos.countByCategoriaId(c.getId());
            res.add(new CategoriaAdminVista(c.getId(), c.getNombre(), c.getIconoSeguro(), count));
        }
        return res;
    }

    @Transactional
    public Categoria guardarCategoria(Integer id, String nombre, String icono) {
        String nom = nombre == null ? "" : nombre.trim();
        if (nom.isEmpty()) {
            throw new OperacionInvalidaException("El nombre es obligatorio.");
        }
        String ic = (icono == null || icono.trim().isEmpty())
                ? Categoria.ICONO_POR_DEFECTO
                : icono.trim();

        Categoria cat;
        if (id != null && id > 0) {
            cat = categorias.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Categoría no encontrada."));
        } else {
            cat = new Categoria();
        }
        cat.setNombre(nom);
        cat.setIcono(ic);
        return categorias.save(cat);
    }

    @Transactional
    public void eliminarCategoria(Integer id) {
        if (id == null || id <= 0) {
            throw new OperacionInvalidaException("Categoría inválida.");
        }
        if (!categorias.existsById(id)) {
            throw new NoSuchElementException("Categoría no encontrada.");
        }
        long asignados = productos.countByCategoriaId(id);
        if (asignados > 0) {
            throw new OperacionInvalidaException("No puedes eliminar una categoría con productos asignados.");
        }
        categorias.deleteById(id);
    }

    /* ── 5. Gestión de tiendas ──────────────────────────────────────────── */

    @Transactional(readOnly = true)
    public List<Tienda> listarTiendasOrdenadasPorNombre() {
        return tiendas.findAllByOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public List<TiendaAdminVista> listarTiendasGestion(String buscar) {
        String q = buscar == null ? "" : buscar.trim();
        List<Tienda> lista = tiendas.buscarAdmin(q);
        List<TiendaAdminVista> resultado = new ArrayList<>(lista.size());
        for (Tienda t : lista) {
            long totalProds = productos.countByTiendaId(t.getId());
            BigDecimal ventasTienda = pedidoItems.findByTiendaId(t.getId()).stream()
                    .map(PedidoItem::getSubtotalEfectivo)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            resultado.add(new TiendaAdminVista(
                    t.getId(),
                    t.getNombre(),
                    t.isActivo(),
                    t.getMotivoSuspension(),
                    t.getUsuario() != null ? t.getUsuario().getNombre() : "—",
                    t.getUsuario() != null ? t.getUsuario().getCorreo() : "—",
                    totalProds,
                    ventasTienda
            ));
        }
        return resultado;
    }

    @Transactional
    public boolean cambiarEstadoTienda(Integer tiendaId, Integer activoParam, String motivo) {
        if (tiendaId == null || tiendaId <= 0 || activoParam == null || (activoParam != 0 && activoParam != 1)) {
            throw new OperacionInvalidaException("Datos inválidos.");
        }
        boolean activar = activoParam == 1;
        String motivoLimpio = motivo == null ? "" : motivo.trim();
        if (!activar && motivoLimpio.isEmpty()) {
            throw new OperacionInvalidaException("Debes indicar un motivo de suspensión.");
        }
        Tienda t = tiendas.findById(tiendaId)
                .orElseThrow(() -> new NoSuchElementException("Tienda no encontrada."));
        t.setActivo(activar);
        t.setMotivoSuspension(activar ? null : motivoLimpio);
        tiendas.save(t);
        return activar;
    }

    /* ── 6. Facturas y pedidos ──────────────────────────────────────────── */

    @Transactional(readOnly = true)
    public List<Pedido> buscarFacturas(String fId, String fCliente) {
        return buscarFacturas(fId, fCliente, "");
    }

    @Transactional(readOnly = true)
    public List<Pedido> buscarFacturas(String fId, String fCliente, String fEstado) {
        Integer pedidoId = null;
        if (fId != null && !fId.isBlank()) {
            String digitos = fId.replaceAll("\\D", "");
            if (!digitos.isEmpty()) {
                try {
                    pedidoId = Integer.parseInt(digitos);
                } catch (NumberFormatException ignored) {
                    pedidoId = -1;
                }
            } else {
                pedidoId = -1;
            }
        }
        String cliente = fCliente == null ? "" : fCliente.trim();
        PedidoEstado estado = parsearEstadoPedido(fEstado);
        return pedidos.buscarFacturasAdmin(pedidoId, cliente, estado, PageRequest.of(0, 50));
    }

    @Transactional
    public PedidoEstado cambiarEstadoPedidoAdmin(Integer pedidoId, String estadoStr) {
        if (pedidoId == null || pedidoId <= 0 || estadoStr == null || estadoStr.isBlank()) {
            throw new OperacionInvalidaException("Datos inválidos.");
        }
        PedidoEstado nuevoEstado;
        try {
            nuevoEstado = PedidoEstado.valueOf(estadoStr.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new OperacionInvalidaException("Datos inválidos.");
        }

        Pedido pedido = pedidos.buscarDetalleAdmin(pedidoId)
                .orElseThrow(() -> new NoSuchElementException("Pedido no encontrado."));

        if (pedido.getEstado() == PedidoEstado.cancelado && nuevoEstado != PedidoEstado.cancelado) {
            throw new OperacionInvalidaException("Un pedido cancelado no puede cambiar de estado.");
        }

        if (pedido.getEstado() != PedidoEstado.cancelado && nuevoEstado == PedidoEstado.cancelado) {
            for (PedidoItem item : pedido.getItems()) {
                if (item.getProducto() != null && item.getCantidad() != null && item.getCantidad() > 0) {
                    productos.restaurarStock(item.getProducto().getId(), item.getCantidad());
                }
            }
            pedido = pedidos.findById(pedidoId)
                    .orElseThrow(() -> new NoSuchElementException("Pedido no encontrado."));
        }

        pedido.setEstado(nuevoEstado);
        pedidos.save(pedido);
        return nuevoEstado;
    }

    @Transactional(readOnly = true)
    public Pedido obtenerFacturaAdmin(Integer pedidoId) {
        return pedidos.buscarDetalleAdmin(pedidoId)
                .orElseThrow(() -> new NoSuchElementException("Pedido no encontrado."));
    }

    /* ── 7. Reportes y exportación CSV ──────────────────────────────────── */

    public static PedidoEstado parsearEstadoPedido(String estadoStr) {
        if (estadoStr == null || estadoStr.isBlank()) return null;
        try {
            return PedidoEstado.valueOf(estadoStr.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public ReporteAdminVista generarReporte(LocalDate desde, LocalDate hasta, String estadoStr, Integer tiendaIdParam) {
        LocalDate d = desde != null ? desde : LocalDate.now().minusDays(30);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        int tiendaId = tiendaIdParam != null && tiendaIdParam > 0 ? tiendaIdParam : 0;
        PedidoEstado estado = parsearEstadoPedido(estadoStr);
        String estadoNormalizado = estado == null ? "" : estado.name();

        LocalDateTime dtDesde = d.atStartOfDay();
        LocalDateTime dtHasta = h.atTime(LocalTime.MAX);

        List<Pedido> pedidosPeriodo = pedidos.buscarParaReporteAdmin(dtDesde, dtHasta, estado);

        BigDecimal totalVentasP = BigDecimal.ZERO;
        long totalPedidosP = 0;
        Map<Integer, TopTiendaAcum> acumTiendas = new HashMap<>();
        Map<Long, TopProdAcum> acumProductos = new HashMap<>();

        if (tiendaId > 0) {
            Set<Integer> pedidosConTienda = new HashSet<>();
            for (Pedido p : pedidosPeriodo) {
                for (PedidoItem it : p.getItems()) {
                    Producto pr = it.getProducto();
                    if (pr != null && pr.getTienda() != null && pr.getTienda().getId().equals(tiendaId)) {
                        BigDecimal sub = it.getSubtotalEfectivo();
                        totalVentasP = totalVentasP.add(sub);
                        pedidosConTienda.add(p.getId());
                        acumProductos.computeIfAbsent(pr.getId(), k -> new TopProdAcum(pr.getId(), pr.getNombre()))
                                .sumar(it.getCantidad() == null ? 0 : it.getCantidad(), sub);
                    }
                }
            }
            totalPedidosP = pedidosConTienda.size();
        } else {
            totalPedidosP = pedidosPeriodo.size();
            for (Pedido p : pedidosPeriodo) {
                totalVentasP = totalVentasP.add(p.getTotal() == null ? BigDecimal.ZERO : p.getTotal());
                for (PedidoItem it : p.getItems()) {
                    Producto pr = it.getProducto();
                    if (pr == null) continue;
                    BigDecimal sub = it.getSubtotalEfectivo();
                    int cant = it.getCantidad() == null ? 0 : it.getCantidad();
                    acumProductos.computeIfAbsent(pr.getId(), k -> new TopProdAcum(pr.getId(), pr.getNombre()))
                            .sumar(cant, sub);
                    Tienda t = pr.getTienda();
                    if (t != null) {
                        acumTiendas.computeIfAbsent(t.getId(), k -> new TopTiendaAcum(t.getId(), t.getNombre()))
                                .sumar(p.getId(), sub);
                    }
                }
            }
        }

        BigDecimal ticketProm = totalPedidosP > 0
                ? totalVentasP.divide(BigDecimal.valueOf(totalPedidosP), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<TopTiendaVista> topTiendas = acumTiendas.values().stream()
                .map(a -> new TopTiendaVista(a.id, a.nombre, a.pedidos.size(), a.total))
                .sorted(Comparator.comparing(TopTiendaVista::total).reversed())
                .limit(5)
                .toList();

        List<TopProductoVista> topProductos = acumProductos.values().stream()
                .map(a -> new TopProductoVista(a.id, a.nombre, a.cantidad, a.total))
                .sorted(Comparator.comparing(TopProductoVista::total).reversed())
                .limit(5)
                .toList();

        List<DiaVentaVista> ultimos14 = calcularUltimos14Dias();

        return new ReporteAdminVista(
                d, h, estadoNormalizado, tiendaId,
                totalVentasP, totalPedidosP, ticketProm,
                topTiendas, topProductos, ultimos14
        );
    }

    private List<DiaVentaVista> calcularUltimos14Dias() {
        LocalDate hoy = LocalDate.now();
        LocalDate inicio = hoy.minusDays(13);
        List<Pedido> recientes = pedidos.buscarParaReporteAdmin(
                inicio.atStartOfDay(), hoy.atTime(LocalTime.MAX), null);

        Map<LocalDate, BigDecimal> porDia = new HashMap<>();
        for (Pedido p : recientes) {
            if (p.getEstado() == PedidoEstado.cancelado || p.getCreadoEn() == null) continue;
            LocalDate dia = p.getCreadoEn().toLocalDate();
            porDia.merge(dia, p.getTotal() == null ? BigDecimal.ZERO : p.getTotal(), BigDecimal::add);
        }

        BigDecimal max = porDia.values().stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ONE);
        if (max.signum() <= 0) max = BigDecimal.ONE;

        List<DiaVentaVista> resultado = new ArrayList<>(14);
        for (int i = 13; i >= 0; i--) {
            LocalDate dia = hoy.minusDays(i);
            BigDecimal total = porDia.getOrDefault(dia, BigDecimal.ZERO);
            int pct = total.signum() <= 0 ? 4
                    : Math.max(4, total.multiply(BigDecimal.valueOf(100))
                    .divide(max, 0, RoundingMode.HALF_UP).intValue());
            resultado.add(new DiaVentaVista(
                    dia.toString(),
                    dia.format(FMT_DIA_CORTO),
                    total,
                    pct
            ));
        }
        return resultado;
    }

    /**
     * Genera el archivo CSV de ventas (con BOM UTF-8 para Excel) respetando los
     * mismos filtros que reporte_csv.php.
     */
    @Transactional(readOnly = true)
    public byte[] exportarCsv(LocalDate desde, LocalDate hasta, String estadoStr, Integer tiendaIdParam) {
        LocalDateTime dtDesde = desde != null ? desde.atStartOfDay() : null;
        LocalDateTime dtHasta = hasta != null ? hasta.atTime(LocalTime.MAX) : null;
        PedidoEstado estado = parsearEstadoPedido(estadoStr);
        int tiendaId = tiendaIdParam != null && tiendaIdParam > 0 ? tiendaIdParam : 0;

        List<Pedido> lista = pedidos.buscarParaReporteAdmin(dtDesde, dtHasta, estado);

        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // BOM UTF-8 para Excel
        sb.append("Pedido,Fecha,Cliente,Correo,Tienda(s),Estado,Total,Dirección\r\n");

        for (Pedido p : lista) {
            if (p.getItems() == null || p.getItems().isEmpty()) continue;

            if (tiendaId > 0) {
                boolean contieneTienda = p.getItems().stream()
                        .anyMatch(i -> i.getProducto() != null
                                && i.getProducto().getTienda() != null
                                && i.getProducto().getTienda().getId().equals(tiendaId));
                if (!contieneTienda) continue;
            }

            String tiendasStr = p.getItems().stream()
                    .filter(i -> i.getProducto() != null && i.getProducto().getTienda() != null)
                    .filter(i -> tiendaId == 0 || i.getProducto().getTienda().getId().equals(tiendaId))
                    .map(i -> i.getProducto().getTienda().getNombre())
                    .distinct()
                    .collect(Collectors.joining(" | "));

            String fecha = p.getCreadoEn() != null ? p.getCreadoEn().format(FMT_FECHA_HORA) : "";
            String clienteNombre = p.getUsuario() != null ? p.getUsuario().getNombre() : "";
            String clienteCorreo = p.getUsuario() != null ? p.getUsuario().getCorreo() : "";

            sb.append(csv(p.getNumeroFactura())).append(',')
              .append(csv(fecha)).append(',')
              .append(csv(clienteNombre)).append(',')
              .append(csv(clienteCorreo)).append(',')
              .append(csv(tiendasStr)).append(',')
              .append(csv(p.getEstado() != null ? p.getEstado().name() : "")).append(',')
              .append(csv(p.getTotal() != null ? p.getTotal().setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00")).append(',')
              .append(csv(p.getDireccion() != null ? p.getDireccion() : ""))
              .append("\r\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String csv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n") || val.contains("\r")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private static final class TopTiendaAcum {
        final Integer id;
        final String nombre;
        final Set<Integer> pedidos = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;

        TopTiendaAcum(Integer id, String nombre) {
            this.id = id;
            this.nombre = nombre;
        }

        void sumar(Integer pedidoId, BigDecimal sub) {
            pedidos.add(pedidoId);
            total = total.add(sub);
        }
    }

    private static final class TopProdAcum {
        final Long id;
        final String nombre;
        long cantidad = 0;
        BigDecimal total = BigDecimal.ZERO;

        TopProdAcum(Long id, String nombre) {
            this.id = id;
            this.nombre = nombre;
        }

        void sumar(int cant, BigDecimal sub) {
            cantidad += cant;
            total = total.add(sub);
        }
    }
}
