package co.cabore.CaboreApplication.service;

import co.cabore.CaboreApplication.model.*;
import co.cabore.CaboreApplication.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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
 * Servicio completo del Módulo Emprendedor (FASE 3 — Miguel).
 *
 * Migra todas las funcionalidades de emprendedor.php, guardar_producto.php,
 * editar_producto.php, actualizar_producto.php, eliminar_producto.php,
 * actualizar_tienda.php, actualizar_pedido.php y factura.php (modo emprendedor).
 */
@Service
@RequiredArgsConstructor
public class EmprendedorService {

    private static final BigDecimal PRECIO_MAXIMO = new BigDecimal("9999999999.99");
    private static final DateTimeFormatter FMT_DIA_CORTO = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FMT_FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TiendaRepository tiendas;
    private final ProductoRepository productos;
    private final CategoriaRepository categorias;
    private final PedidoRepository pedidos;
    private final PedidoItemRepository pedidoItems;
    private final UsuarioRepository usuarios;
    private final MensajeRepository mensajes;
    private final ProductoService productoService;
    private final ImagenService imagenService;
    private final TiendaPersonalizacionService personalizacionService;

    /* ── DTOs / Records de vista ────────────────────────────────────────── */

    public record ItemVentaVista(
            Integer itemId,
            Long productoId,
            String productoNombre,
            String productoImagen,
            int cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal
    ) {}

    public record PedidoTiendaVista(
            Integer pedidoId,
            String numeroFactura,
            PedidoEstado estado,
            LocalDateTime creadoEn,
            Integer compradorId,
            String compradorNombre,
            String compradorCorreo,
            String compradorTelefono,
            String direccion,
            String notas,
            List<ItemVentaVista> items,
            BigDecimal totalTienda
    ) {}

    public record ProductoMasVendidoVista(
            Long id,
            String nombre,
            String rutaImagen,
            long cantidad,
            BigDecimal total,
            int barraPct,
            int porcentajeVentas
    ) {}

    public record DiaVentaVista(
            String fecha,
            String label,
            BigDecimal total,
            int alturaPct
    ) {}

    public record ResumenVentasTienda(
            BigDecimal totalVentas,
            long totalPedidosVenta,
            long totalPendientes,
            BigDecimal ticketPromedio,
            ProductoMasVendidoVista productoEstrella,
            List<ProductoMasVendidoVista> masVendidos,
            List<DiaVentaVista> ultimos7dias,
            List<PedidoTiendaVista> pedidosAgrupados
    ) {}

    public record ReporteTiendaVista(
            LocalDate desde,
            LocalDate hasta,
            String estado,
            BigDecimal totalVentas,
            long totalPedidos,
            BigDecimal ticketPromedio,
            List<ProductoMasVendidoVista> topProductos,
            List<DiaVentaVista> ultimos14
    ) {}

    /* ── 1. Tienda del emprendedor (auto-creación si no existe) ─────────── */

    @Transactional
    public Tienda obtenerOCrearTienda(Integer usuarioId) {
        return tiendas.findByUsuarioId(usuarioId).orElseGet(() -> {
            Usuario u = usuarios.findById(usuarioId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado."));
            Tienda nueva = new Tienda();
            nueva.setUsuario(u);
            nueva.setNombre("Mi tienda");
            nueva.setDescripcion("Descripción de mi tienda");
            nueva.setColor("#c9a84c");
            nueva.setPlantilla("moderna");
            nueva.setActivo(true);
            return tiendas.save(nueva);
        });
    }

    @Transactional(readOnly = true)
    public Tienda exigirTienda(Integer usuarioId) {
        return tiendas.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Tu cuenta no tiene una tienda asociada."));
    }

    @Transactional(readOnly = true)
    public long contarMensajesNoLeidos(Integer tiendaId, Integer usuarioId) {
        return mensajes.contarNoLeidosTienda(tiendaId, usuarioId);
    }

    /* ── 2. Categorías e inventario ─────────────────────────────────────── */

    @Transactional(readOnly = true)
    public List<Categoria> listarCategorias() {
        return categorias.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public Map<Integer, Categoria> mapaCategorias() {
        Map<Integer, Categoria> map = new LinkedHashMap<>();
        for (Categoria c : categorias.findAllByOrderByIdAsc()) {
            map.put(c.getId(), c);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public List<Producto> listarProductosTienda(Integer tiendaId) {
        return productos.findByTiendaIdOrderByIdDesc(tiendaId);
    }

    public static BigDecimal calcularValorInventario(List<Producto> lista) {
        BigDecimal total = BigDecimal.ZERO;
        for (Producto p : lista) {
            BigDecimal precio = p.getPrecio() == null ? BigDecimal.ZERO : p.getPrecio();
            int stock = p.getStock() == null || p.getStock() < 0 ? 0 : p.getStock();
            total = total.add(precio.multiply(BigDecimal.valueOf(stock)));
        }
        return total;
    }

    /* ── 3. Gestión de productos (crear, editar, actualizar, eliminar) ───── */

    @Transactional(readOnly = true)
    public Producto obtenerProductoPropio(Long productoId, Tienda tienda) {
        Producto p = productos.findById(productoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado."));
        if (p.getTienda() == null || !p.getTienda().getId().equals(tienda.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este producto no pertenece a tu tienda.");
        }
        return p;
    }

    @Transactional
    public Producto crearProducto(Tienda tienda,
                                  String nombre,
                                  String descripcion,
                                  BigDecimal precio,
                                  BigDecimal precioOferta,
                                  Integer categoriaId,
                                  Integer stock,
                                  boolean destacado,
                                  boolean activo,
                                  MultipartFile imagen) {
        String error = validarProducto(nombre, precio, precioOferta, stock);
        if (error != null) {
            throw new OperacionInvalidaException(error);
        }

        Producto p = new Producto();
        p.setTienda(tienda);
        p.setNombre(nombre.trim());
        p.setDescripcion(limpiar(descripcion));
        p.setPrecio(precio.setScale(2, RoundingMode.HALF_UP));
        p.setPrecioOferta(normalizarOferta(precio, precioOferta));
        p.setCategoriaId(categoriaId != null && categoriaId > 0 ? categoriaId : Producto.CATEGORIA_POR_DEFECTO);
        p.setStock(stock == null ? 0 : Math.max(0, stock));
        p.setDestacado(destacado);
        p.setActivo(activo);
        p.setEstadoVerificacion(EstadoVerificacion.pendiente);

        String rutaImg = imagenService.guardar(imagen, "productos", "prod");
        if (rutaImg != null) {
            p.setImagen(rutaImg);
        }

        return productos.save(p);
    }

    @Transactional
    public Producto actualizarProducto(Tienda tienda,
                                       Long productoId,
                                       String nombre,
                                       String descripcion,
                                       BigDecimal precio,
                                       BigDecimal precioOferta,
                                       Integer categoriaId,
                                       Integer stock,
                                       Boolean destacado,
                                       Boolean activo,
                                       MultipartFile imagen) {
        Producto p = obtenerProductoPropio(productoId, tienda);

        String error = validarProducto(nombre, precio, precioOferta, stock);
        if (error != null) {
            throw new OperacionInvalidaException(error);
        }

        p.setNombre(nombre.trim());
        p.setDescripcion(limpiar(descripcion));
        p.setPrecio(precio.setScale(2, RoundingMode.HALF_UP));
        p.setPrecioOferta(normalizarOferta(precio, precioOferta));
        if (categoriaId != null && categoriaId > 0) {
            p.setCategoriaId(categoriaId);
        } else if (p.getCategoriaId() == null) {
            p.setCategoriaId(Producto.CATEGORIA_POR_DEFECTO);
        }
        p.setStock(stock == null ? 0 : Math.max(0, stock));
        if (destacado != null) {
            p.setDestacado(destacado);
        }
        if (activo != null) {
            p.setActivo(activo);
        }

        String rutaImg = imagenService.guardar(imagen, "productos", "prod");
        if (rutaImg != null) {
            p.setImagen(rutaImg);
        }

        // Si estaba rechazado y el emprendedor lo corrige, vuelve a pendiente para nueva revisión
        if (p.getEstadoVerificacion() == EstadoVerificacion.rechazado) {
            p.setEstadoVerificacion(EstadoVerificacion.pendiente);
            p.setMotivoRechazo(null);
        }

        return productos.save(p);
    }

    @Transactional
    public ProductoService.ResultadoEliminacion eliminarProducto(Tienda tienda, Long productoId) {
        Producto p = obtenerProductoPropio(productoId, tienda);
        return productoService.eliminar(p);
    }

    /** Actualización rápida de stock desde la tabla/grilla de inventario del emprendedor. */
    @Transactional
    public int actualizarStockRapido(Tienda tienda, Long productoId, Integer nuevoStock) {
        if (productoId == null || productoId <= 0 || nuevoStock == null || nuevoStock < 0) {
            throw new OperacionInvalidaException("El stock debe ser un número mayor o igual a 0.");
        }
        Producto p = obtenerProductoPropio(productoId, tienda);
        p.setStock(nuevoStock);
        productos.save(p);
        return nuevoStock;
    }

    /** Alterna rápidamente el estado activo o destacado de un producto propio. */
    @Transactional
    public Producto alternarCampoRapido(Tienda tienda, Long productoId, String campo) {
        if (productoId == null || productoId <= 0 || campo == null) {
            throw new OperacionInvalidaException("Datos inválidos.");
        }
        Producto p = obtenerProductoPropio(productoId, tienda);
        switch (campo.trim().toLowerCase()) {
            case "activo" -> p.setActivo(!p.isActivo());
            case "destacado" -> p.setDestacado(!p.isDestacado());
            default -> throw new OperacionInvalidaException("Campo no válido.");
        }
        return productos.save(p);
    }

    public static String validarProducto(String nombre, BigDecimal precio, BigDecimal precioOferta, Integer stock) {
        if (nombre == null || nombre.trim().length() < 2) {
            return "El nombre debe tener al menos 2 caracteres.";
        }
        if (precio == null || precio.signum() <= 0) {
            return "El precio debe ser mayor a 0.";
        }
        if (precio.compareTo(PRECIO_MAXIMO) > 0) {
            return "El precio es demasiado alto.";
        }
        if (precioOferta != null && precioOferta.signum() > 0 && precioOferta.compareTo(precio) >= 0) {
            return "El precio de oferta debe ser menor al precio normal.";
        }
        if (stock == null || stock < 0) {
            return "El stock no puede ser negativo.";
        }
        return null;
    }

    private static BigDecimal normalizarOferta(BigDecimal precio, BigDecimal oferta) {
        if (oferta == null || oferta.signum() <= 0 || oferta.compareTo(precio) >= 0) {
            return null;
        }
        return oferta.setScale(2, RoundingMode.HALF_UP);
    }

    /* ── 4. Configuración y personalización de tienda (actualizar_tienda.php) ── */

    @Transactional
    public Tienda actualizarInformacionTienda(Tienda tienda, String nombre, String descripcion, String ciudad) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new OperacionInvalidaException("El nombre de la tienda no puede estar vacío.");
        }
        tienda.setNombre(nombre.trim());
        tienda.setDescripcion(limpiar(descripcion));
        tienda.setCiudad(limpiar(ciudad));
        Tienda guardada = tiendas.save(tienda);
        personalizacionService.sincronizarDesdeTienda(guardada);
        return guardada;
    }

    @Transactional
    public Tienda actualizarAparienciaTienda(Tienda tienda,
                                             Integer usuarioId,
                                             String color,
                                             String plantilla,
                                             MultipartFile logo,
                                             MultipartFile banner) {
        String colorFinal = (color != null && color.trim().matches("^#[0-9a-fA-F]{6}$"))
                ? color.trim()
                : "#c9a84c";
        String plantillaFinal = (plantilla != null && Tienda.PLANTILLAS.contains(plantilla.trim().toLowerCase()))
                ? plantilla.trim().toLowerCase()
                : "moderna";

        tienda.setColor(colorFinal);
        tienda.setPlantilla(plantillaFinal);

        String rutaLogo = imagenService.guardar(logo, "logos", "logo_" + usuarioId);
        if (rutaLogo != null) {
            tienda.setLogo(rutaLogo);
        }

        String rutaBanner = imagenService.guardar(banner, "banners", "banner_" + usuarioId);
        if (rutaBanner != null) {
            tienda.setBanner(rutaBanner);
        }

        Tienda guardada = tiendas.save(tienda);
        personalizacionService.sincronizarDesdeTienda(guardada);
        return guardada;
    }

    @Transactional
    public Tienda actualizarContactoTienda(Tienda tienda, String whatsapp, String instagram) {
        String wa = whatsapp == null ? "" : whatsapp.replaceAll("[^0-9]", "");
        String ig = instagram == null ? "" : instagram.trim();
        while (ig.startsWith("@")) {
            ig = ig.substring(1).trim();
        }
        tienda.setWhatsapp(wa.isEmpty() ? null : wa);
        tienda.setInstagram(ig.isEmpty() ? null : ig);
        Tienda guardada = tiendas.save(tienda);
        personalizacionService.sincronizarDesdeTienda(guardada);
        return guardada;
    }

    /* ── 5. Estadísticas de ventas, producto estrella, ranking y pedidos ── */

    @Transactional(readOnly = true)
    public ResumenVentasTienda cargarVentasTienda(Integer tiendaId) {
        List<PedidoItem> filas = pedidoItems.findByTiendaId(tiendaId);

        BigDecimal totalVentas = BigDecimal.ZERO;
        Set<Integer> pedidosIds = new HashSet<>();
        Set<Integer> pendientesIds = new HashSet<>();
        Map<LocalDate, BigDecimal> ventasPorDia = new HashMap<>();
        Map<Long, ProdAcum> acumProd = new LinkedHashMap<>();
        Map<Integer, PedidoAcum> acumPedidos = new LinkedHashMap<>();

        for (PedidoItem it : filas) {
            Pedido p = it.getPedido();
            Producto pr = it.getProducto();
            int cant = it.getCantidad() == null ? 0 : it.getCantidad();
            BigDecimal precioUnit = it.getPrecioEfectivo();
            BigDecimal sub = it.getSubtotalEfectivo();

            totalVentas = totalVentas.add(sub);
            pedidosIds.add(p.getId());
            if (p.getEstado() == PedidoEstado.pendiente) {
                pendientesIds.add(p.getId());
            }
            if (p.getCreadoEn() != null) {
                ventasPorDia.merge(p.getCreadoEn().toLocalDate(), sub, BigDecimal::add);
            }

            acumProd.computeIfAbsent(pr.getId(), k -> new ProdAcum(pr.getId(), pr.getNombre(), pr.getRutaImagen()))
                    .sumar(cant, sub);

            PedidoAcum pa = acumPedidos.computeIfAbsent(p.getId(), k -> new PedidoAcum(p));
            pa.items.add(new ItemVentaVista(
                    it.getId(),
                    pr.getId(),
                    pr.getNombre(),
                    pr.getRutaImagen(),
                    cant,
                    precioUnit,
                    sub
            ));
            pa.total = pa.total.add(sub);
        }

        long totalPedidosVenta = pedidosIds.size();
        long totalPendientes = pendientesIds.size();
        BigDecimal ticketPromedio = totalPedidosVenta > 0
                ? totalVentas.divide(BigDecimal.valueOf(totalPedidosVenta), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Ranking top 5 productos más vendidos (ordenados por cantidad desc, como en emprendedor.php)
        List<ProdAcum> ordenados = acumProd.values().stream()
                .sorted(Comparator.comparingLong((ProdAcum a) -> a.cantidad).reversed()
                        .thenComparing(a -> a.total, Comparator.reverseOrder()))
                .limit(5)
                .toList();

        long maxCant = ordenados.isEmpty() ? 1 : Math.max(1, ordenados.get(0).cantidad);
        BigDecimal ventasRef = totalVentas;
        List<ProductoMasVendidoVista> masVendidos = ordenados.stream()
                .map(a -> {
                    int barra = (int) Math.round(a.cantidad * 100.0 / maxCant);
                    int pctVentas = ventasRef.signum() > 0
                            ? a.total.multiply(BigDecimal.valueOf(100))
                            .divide(ventasRef, 0, RoundingMode.HALF_UP).intValue()
                            : 0;
                    return new ProductoMasVendidoVista(
                            a.id, a.nombre, a.rutaImagen, a.cantidad, a.total, barra, pctVentas);
                })
                .toList();

        ProductoMasVendidoVista estrella = masVendidos.isEmpty() ? null : masVendidos.get(0);

        // Últimos 7 días
        List<DiaVentaVista> ultimos7 = construirBarrasDias(ventasPorDia, 7);

        // Pedidos agrupados ordenados por fecha desc
        List<PedidoTiendaVista> pedidosAgrupados = acumPedidos.values().stream()
                .sorted(Comparator.comparing((PedidoAcum a) ->
                        a.pedido.getCreadoEn() == null ? LocalDateTime.MIN : a.pedido.getCreadoEn()).reversed())
                .map(a -> {
                    Usuario u = a.pedido.getUsuario();
                    String tel = a.pedido.getTelefonoContacto() != null && !a.pedido.getTelefonoContacto().isBlank()
                            ? a.pedido.getTelefonoContacto()
                            : (u != null ? u.getTelefono() : null);
                    return new PedidoTiendaVista(
                            a.pedido.getId(),
                            a.pedido.getNumeroFactura(),
                            a.pedido.getEstado(),
                            a.pedido.getCreadoEn(),
                            u != null ? u.getId() : null,
                            u != null ? u.getNombre() : "Cliente",
                            u != null ? u.getCorreo() : "",
                            tel,
                            a.pedido.getDireccion(),
                            a.pedido.getNotas(),
                            a.items,
                            a.total
                    );
                })
                .toList();

        return new ResumenVentasTienda(
                totalVentas,
                totalPedidosVenta,
                totalPendientes,
                ticketPromedio,
                estrella,
                masVendidos,
                ultimos7,
                pedidosAgrupados
        );
    }

    /* ── 6. Cambio de estado de pedido por el emprendedor (actualizar_pedido.php) ── */

    @Transactional
    public PedidoEstado cambiarEstadoPedido(Tienda tienda, Integer pedidoId, String estadoStr) {
        if (pedidoId == null || pedidoId <= 0 || estadoStr == null || estadoStr.isBlank()) {
            throw new OperacionInvalidaException("Datos inválidos.");
        }
        PedidoEstado nuevoEstado;
        try {
            nuevoEstado = PedidoEstado.valueOf(estadoStr.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new OperacionInvalidaException("Datos inválidos.");
        }

        long itemsPropios = pedidoItems.contarItemsDeTiendaEnPedido(pedidoId, tienda.getId());
        if (itemsPropios == 0) {
            throw new OperacionInvalidaException("Este pedido no pertenece a tu tienda.");
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

    /* ── 7. Reportes de la tienda y exportación CSV ─────────────────────── */

    @Transactional(readOnly = true)
    public ReporteTiendaVista generarReporteTienda(Integer tiendaId,
                                                   LocalDate desde,
                                                   LocalDate hasta,
                                                   String estadoStr) {
        LocalDate d = desde != null ? desde : LocalDate.now().minusDays(30);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        PedidoEstado estadoFiltro = AdminService.parsearEstadoPedido(estadoStr);
        String estadoNormalizado = estadoFiltro == null ? "" : estadoFiltro.name();

        LocalDateTime dtDesde = d.atStartOfDay();
        LocalDateTime dtHasta = h.atTime(LocalTime.MAX);

        List<PedidoItem> todos = pedidoItems.findByTiendaId(tiendaId);

        BigDecimal totalVentasP = BigDecimal.ZERO;
        Set<Integer> pedidosSet = new HashSet<>();
        Map<Long, ProdAcum> acumTop = new HashMap<>();
        Map<LocalDate, BigDecimal> ventas14Dias = new HashMap<>();

        LocalDate inicio14 = LocalDate.now().minusDays(13);

        for (PedidoItem it : todos) {
            Pedido p = it.getPedido();
            LocalDateTime fecha = p.getCreadoEn();
            BigDecimal sub = it.getSubtotalEfectivo();

            // Acumular últimos 14 días (excluyendo cancelados, igual que emprendedor.php línea 200)
            if (fecha != null && !fecha.toLocalDate().isBefore(inicio14) && p.getEstado() != PedidoEstado.cancelado) {
                ventas14Dias.merge(fecha.toLocalDate(), sub, BigDecimal::add);
            }

            // Filtrar por rango de fechas y estado del reporte
            if (fecha != null && (fecha.isBefore(dtDesde) || fecha.isAfter(dtHasta))) {
                continue;
            }
            if (estadoFiltro != null && p.getEstado() != estadoFiltro) {
                continue;
            }

            totalVentasP = totalVentasP.add(sub);
            pedidosSet.add(p.getId());
            Producto pr = it.getProducto();
            int cant = it.getCantidad() == null ? 0 : it.getCantidad();
            acumTop.computeIfAbsent(pr.getId(), k -> new ProdAcum(pr.getId(), pr.getNombre(), pr.getRutaImagen()))
                    .sumar(cant, sub);
        }

        long totalPedidosP = pedidosSet.size();
        BigDecimal ticketPromP = totalPedidosP > 0
                ? totalVentasP.divide(BigDecimal.valueOf(totalPedidosP), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<ProductoMasVendidoVista> topProductos = acumTop.values().stream()
                .sorted(Comparator.comparing((ProdAcum a) -> a.total).reversed())
                .limit(5)
                .map(a -> new ProductoMasVendidoVista(a.id, a.nombre, a.rutaImagen, a.cantidad, a.total, 100, 0))
                .toList();

        List<DiaVentaVista> ultimos14 = construirBarrasDias(ventas14Dias, 14);

        return new ReporteTiendaVista(
                d, h, estadoNormalizado,
                totalVentasP, totalPedidosP, ticketPromP,
                topProductos, ultimos14
        );
    }

    @Transactional(readOnly = true)
    public byte[] exportarCsvTienda(Tienda tienda, LocalDate desde, LocalDate hasta, String estadoStr) {
        LocalDate d = desde != null ? desde : LocalDate.now().minusDays(30);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        PedidoEstado estadoFiltro = AdminService.parsearEstadoPedido(estadoStr);
        LocalDateTime dtDesde = d.atStartOfDay();
        LocalDateTime dtHasta = h.atTime(LocalTime.MAX);

        List<PedidoTiendaVista> pedidosTienda = cargarVentasTienda(tienda.getId()).pedidosAgrupados();

        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // BOM UTF-8 para Excel
        sb.append("Pedido,Fecha,Cliente,Correo,Productos,Estado,Total Tienda,Dirección\r\n");

        for (PedidoTiendaVista p : pedidosTienda) {
            if (p.creadoEn() != null && (p.creadoEn().isBefore(dtDesde) || p.creadoEn().isAfter(dtHasta))) {
                continue;
            }
            if (estadoFiltro != null && p.estado() != estadoFiltro) {
                continue;
            }
            String prodsStr = p.items().stream()
                    .map(i -> i.cantidad() + "x " + i.productoNombre())
                    .collect(Collectors.joining(" | "));
            String fecha = p.creadoEn() != null ? p.creadoEn().format(FMT_FECHA_HORA) : "";

            sb.append(csv(p.numeroFactura())).append(',')
              .append(csv(fecha)).append(',')
              .append(csv(p.compradorNombre())).append(',')
              .append(csv(p.compradorCorreo())).append(',')
              .append(csv(prodsStr)).append(',')
              .append(csv(p.estado() != null ? p.estado().name() : "")).append(',')
              .append(csv(p.totalTienda().setScale(2, RoundingMode.HALF_UP).toPlainString())).append(',')
              .append(csv(p.direccion() != null ? p.direccion() : ""))
              .append("\r\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /* ── 8. Facturas de la tienda (factura.php modo emprendedor) ────────── */

    @Transactional(readOnly = true)
    public List<PedidoTiendaVista> buscarFacturasTienda(Integer tiendaId, String fId) {
        List<PedidoTiendaVista> todas = cargarVentasTienda(tiendaId).pedidosAgrupados();
        if (fId == null || fId.isBlank()) {
            return todas.size() > 100 ? todas.subList(0, 100) : todas;
        }
        String digitos = fId.replaceAll("\\D", "");
        if (digitos.isEmpty()) return List.of();
        try {
            int buscado = Integer.parseInt(digitos);
            return todas.stream().filter(p -> p.pedidoId().equals(buscado)).toList();
        } catch (NumberFormatException e) {
            return List.of();
        }
    }

    /**
     * Devuelve una copia de vista del Pedido que contiene ÚNICAMENTE los ítems
     * pertenecientes a la tienda del emprendedor y su subtotal correspondiente,
     * tal como hace factura.php cuando el usuario es emprendedor.
     */
    @Transactional(readOnly = true)
    public Pedido obtenerFacturaEmprendedor(Tienda tienda, Integer pedidoId) {
        Pedido original = pedidos.buscarDetalleAdmin(pedidoId)
                .orElseThrow(() -> new NoSuchElementException("Pedido no encontrado."));

        List<PedidoItem> itemsPropios = original.getItems().stream()
                .filter(i -> i.getProducto() != null
                        && i.getProducto().getTienda() != null
                        && i.getProducto().getTienda().getId().equals(tienda.getId()))
                .toList();

        if (itemsPropios.isEmpty()) {
            throw new OperacionInvalidaException("Esta factura no tiene productos de tu tienda.");
        }

        BigDecimal subtotalTienda = itemsPropios.stream()
                .map(PedidoItem::getSubtotalEfectivo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Pedido vista = new Pedido();
        vista.setId(original.getId());
        vista.setUsuario(original.getUsuario());
        vista.setEstado(original.getEstado());
        vista.setDireccion(original.getDireccion());
        vista.setTelefonoContacto(original.getTelefonoContacto());
        vista.setNotas(original.getNotas());
        vista.setCreadoEn(original.getCreadoEn());
        vista.setTotal(subtotalTienda);
        vista.setItems(new ArrayList<>(itemsPropios));
        return vista;
    }

    /* ── Utilidades internas ────────────────────────────────────────────── */

    private static List<DiaVentaVista> construirBarrasDias(Map<LocalDate, BigDecimal> porDia, int cantidadDias) {
        LocalDate hoy = LocalDate.now();
        BigDecimal max = BigDecimal.ONE;
        for (int i = cantidadDias - 1; i >= 0; i--) {
            BigDecimal v = porDia.getOrDefault(hoy.minusDays(i), BigDecimal.ZERO);
            if (v.compareTo(max) > 0) max = v;
        }
        List<DiaVentaVista> lista = new ArrayList<>(cantidadDias);
        for (int i = cantidadDias - 1; i >= 0; i--) {
            LocalDate dia = hoy.minusDays(i);
            BigDecimal total = porDia.getOrDefault(dia, BigDecimal.ZERO);
            int pct = total.signum() <= 0 ? 4
                    : Math.max(4, total.multiply(BigDecimal.valueOf(100))
                    .divide(max, 0, RoundingMode.HALF_UP).intValue());
            lista.add(new DiaVentaVista(dia.toString(), dia.format(FMT_DIA_CORTO), total, pct));
        }
        return lista;
    }

    private static String limpiar(String texto) {
        return (texto == null || texto.isBlank()) ? null : texto.trim();
    }

    private static String csv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n") || val.contains("\r")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private static final class ProdAcum {
        final Long id;
        final String nombre;
        final String rutaImagen;
        long cantidad = 0;
        BigDecimal total = BigDecimal.ZERO;

        ProdAcum(Long id, String nombre, String rutaImagen) {
            this.id = id;
            this.nombre = nombre;
            this.rutaImagen = rutaImagen;
        }

        void sumar(int cant, BigDecimal sub) {
            this.cantidad += cant;
            this.total = this.total.add(sub);
        }
    }

    private static final class PedidoAcum {
        final Pedido pedido;
        final List<ItemVentaVista> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        PedidoAcum(Pedido pedido) {
            this.pedido = pedido;
        }
    }
}
