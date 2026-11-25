package co.cabore.CaboreApplication.service;

import co.cabore.CaboreApplication.model.Pedido;
import co.cabore.CaboreApplication.model.PedidoEstado;
import co.cabore.CaboreApplication.repository.PedidoRepository;
import co.cabore.CaboreApplication.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Gestión e historial de pedidos del cliente autenticado.
 * Cada consulta filtra por usuario_id del usuario autenticado: un cliente
 * nunca puede abrir el pedido de otro, aunque escriba la URL a mano.
 */
@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidos;
    private final ProductoRepository productos;
    private final CarritoService carrito;

    public record ResumenPedidosCliente(
            long total,
            long enCurso,
            long entregados,
            java.math.BigDecimal totalInvertido
    ) {}

    @Transactional(readOnly = true)
    public List<Pedido> historial(Integer usuarioId) {
        return pedidos.findByUsuarioIdOrderByCreadoEnDesc(usuarioId);
    }

    @Transactional(readOnly = true)
    public List<Pedido> historialFiltrado(Integer usuarioId, String estadoStr, String q) {
        List<Pedido> todos = historial(usuarioId);
        PedidoEstado estadoFiltro = null;
        if (estadoStr != null && !estadoStr.isBlank()) {
            try {
                estadoFiltro = PedidoEstado.valueOf(estadoStr.trim().toLowerCase());
            } catch (IllegalArgumentException ignored) {
                estadoFiltro = null;
            }
        }
        String qNorm = q == null ? "" : q.trim().toLowerCase();
        PedidoEstado ef = estadoFiltro;
        return todos.stream()
                .filter(p -> ef == null || p.getEstado() == ef)
                .filter(p -> {
                    if (qNorm.isEmpty()) return true;
                    if (String.valueOf(p.getId()).contains(qNorm)) return true;
                    if (p.getNumeroFactura() != null && p.getNumeroFactura().toLowerCase().contains(qNorm)) return true;
                    return p.getItems() != null && p.getItems().stream()
                            .anyMatch(i -> i.getProducto() != null
                                    && i.getProducto().getNombre() != null
                                    && i.getProducto().getNombre().toLowerCase().contains(qNorm));
                })
                .toList();
    }

    public static ResumenPedidosCliente calcularResumen(List<Pedido> todos) {
        long total = todos.size();
        long enCurso = todos.stream()
                .filter(p -> p.getEstado() == PedidoEstado.pendiente
                        || p.getEstado() == PedidoEstado.confirmado
                        || p.getEstado() == PedidoEstado.enviado)
                .count();
        long entregados = todos.stream()
                .filter(p -> p.getEstado() == PedidoEstado.entregado)
                .count();
        java.math.BigDecimal invertido = todos.stream()
                .filter(p -> p.getEstado() != PedidoEstado.cancelado)
                .map(p -> p.getTotal() == null ? java.math.BigDecimal.ZERO : p.getTotal())
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        return new ResumenPedidosCliente(total, enCurso, entregados, invertido);
    }

    /**
     * Vuelve a agregar al carrito del cliente los productos disponibles de un pedido anterior.
     * Devuelve la cantidad de productos distintos agregados al carrito.
     */
    @Transactional
    public int reordenar(Integer pedidoId, Integer usuarioId) {
        Pedido pedido = pedidos.buscarDetalle(pedidoId, usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Pedido no encontrado."));
        int agregados = 0;
        for (var item : pedido.getItems()) {
            var prod = item.getProducto();
            if (prod != null && prod.isDisponible()
                    && prod.getTienda() != null && prod.getTienda().isActivo()) {
                int cant = item.getCantidad() == null || item.getCantidad() <= 0 ? 1 : item.getCantidad();
                int cantReal = Math.min(cant, prod.getStock());
                if (cantReal > 0) {
                    carrito.agregar(usuarioId, prod.getId(), cantReal);
                    agregados++;
                }
            }
        }
        if (agregados == 0) {
            throw new OperacionInvalidaException(
                    "Ninguno de los productos de este pedido tiene stock disponible en este momento.");
        }
        return agregados;
    }

    @Transactional(readOnly = true)
    public Pedido detalle(Integer pedidoId, Integer usuarioId) {
        return pedidos.buscarDetalle(pedidoId, usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Pedido no encontrado."));
    }

    /**
     * Cancelación real y transaccional: solo pedidos pendientes. Devuelve el
     * stock de cada producto a la tabla productos y guarda estado='cancelado'.
     */
    @Transactional
    public Pedido cancelar(Integer pedidoId, Integer usuarioId) {
        Pedido pedido = pedidos.buscarDetalle(pedidoId, usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Pedido no encontrado."));
        if (pedido.getEstado() != PedidoEstado.pendiente) {
            throw new OperacionInvalidaException(
                    "Solo puedes cancelar un pedido mientras esté pendiente. Estado actual: "
                            + pedido.getEstado().etiqueta().toLowerCase() + ".");
        }
        // 1) Restaurar stock (updates atómicos; vacían el contexto de persistencia).
        pedido.getItems().forEach(item ->
                productos.restaurarStock(item.getProducto().getId(), item.getCantidad()));
        // 2) Marcar cancelado sobre una instancia gestionada reciente.
        Pedido gestionado = pedidos.findById(pedidoId)
                .orElseThrow(() -> new NoSuchElementException("Pedido no encontrado."));
        gestionado.setEstado(PedidoEstado.cancelado);
        return gestionado; // el flush ocurre al cerrar la transacción
    }
}
