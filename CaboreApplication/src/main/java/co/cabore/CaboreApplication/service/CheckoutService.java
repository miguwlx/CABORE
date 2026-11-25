package co.cabore.CaboreApplication.service;

import co.cabore.CaboreApplication.model.CarritoItem;
import co.cabore.CaboreApplication.model.Pedido;
import co.cabore.CaboreApplication.model.PedidoEstado;
import co.cabore.CaboreApplication.model.PedidoItem;
import co.cabore.CaboreApplication.model.Producto;
import co.cabore.CaboreApplication.repository.CarritoItemRepository;
import co.cabore.CaboreApplication.repository.PedidoRepository;
import co.cabore.CaboreApplication.repository.ProductoRepository;
import co.cabore.CaboreApplication.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Compra real (fase 3): convierte el carrito en un pedido persistido en
 * MariaDB dentro de UNA sola transacción:
 *
 *   1. FASE DE LECTURA — revalida contra la base de datos que cada producto
 *      siga en venta (activo, aprobado, con stock y tienda activa) y congela
 *      el precio vigente. El navegador nunca envía precios.
 *   2. FASE DE ESCRITURA — descuenta el stock con UPDATE atómico condicionado
 *      a stock >= cantidad; si en algún producto no alcanza, se aborta con
 *      CheckoutException y la transacción completa se revierte (ni pedido a
 *      medias, ni stock descontado a medias).
 *   3. Escribe pedido (estado 'pendiente', como en el sistema original) e
 *      ítems con el precio congelado, y limpia las filas compradas del carrito.
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    static final int MAX_CANTIDAD_POR_PRODUCTO = 99;

    private final PedidoRepository pedidos;
    private final ProductoRepository productos;
    private final CarritoItemRepository carrito;
    private final UsuarioRepository usuarios;
    private final CarritoService carritoService;

    /**
     * Foto validada de una línea del carrito, lista para congelar en pedido_items.
     * Se guarda la entidad Producto ya inicializada (no un proxy): las consultas
     * @Modifying con clearAutomatically desligan el contexto de persistencia a
     * mitad del flujo y un proxy sin sesión reventaría al pintar la página.
     */
    private record Linea(Producto producto, int cantidad, BigDecimal precioUnit) {}

    @Transactional
    public Pedido comprar(Integer usuarioId, String direccion, String telefono, String notas) {
        if (direccion == null || direccion.isBlank()) {
            throw new CheckoutException("Por favor ingresa tu dirección de entrega.");
        }

        List<CarritoItem> filas = carritoService.filas(usuarioId);
        if (filas.isEmpty()) {
            throw new CheckoutException("El carrito está vacío.");
        }

        // 1) FASE DE LECTURA: validar y congelar precios contra la BD.
        List<Linea> lineas = new ArrayList<>();
        for (CarritoItem fila : filas) {
            Producto p = fila.getProducto();
            if (p == null) {
                throw new CheckoutException("Uno de los productos del carrito ya no existe.");
            }
            if (!p.isDisponible() || p.getTienda() == null || !p.getTienda().isActivo()) {
                throw new CheckoutException("«" + p.getNombre() + "» ya no está disponible. Actualiza tu carrito.");
            }
            int cantidad = Math.min(Math.max(1, fila.getCantidad()), MAX_CANTIDAD_POR_PRODUCTO);
            BigDecimal precioUnit = p.getPrecioActual().setScale(2, RoundingMode.HALF_UP);
            lineas.add(new Linea(p, cantidad, precioUnit));
        }

        // 2) FASE DE ESCRITURA: stock atómico + pedido + ítems.
        Pedido pedido = new Pedido();
        pedido.setUsuario(usuarios.getReferenceById(usuarioId));
        pedido.setEstado(PedidoEstado.pendiente);
        pedido.setDireccion(direccion.trim());
        pedido.setTelefonoContacto(telefono == null || telefono.isBlank() ? null : telefono.trim());
        pedido.setNotas(notas == null ? "" : notas.trim());

        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<Long> comprados = new ArrayList<>();

        for (Linea l : lineas) {
            int descontado = productos.descontarStock(l.producto().getId(), l.cantidad());
            if (descontado == 0) {
                throw new CheckoutException("No hay stock suficiente de «" + l.producto().getNombre() + "».");
            }
            BigDecimal subtotal = l.precioUnit()
                    .multiply(BigDecimal.valueOf(l.cantidad()))
                    .setScale(2, RoundingMode.HALF_UP);

            PedidoItem item = new PedidoItem();
            item.setProducto(l.producto());
            item.setCantidad(l.cantidad());
            item.setPrecioUnit(l.precioUnit());
            item.setPrecioUnitario(l.precioUnit()); // compatibilidad con la lectura legacy de facturas
            item.setSubtotal(subtotal);
            pedido.addItem(item);

            total = total.add(subtotal);
            comprados.add(l.producto().getId());
        }

        pedido.setTotal(total);
        Pedido guardado = pedidos.save(pedido); // IDENTITY: insert inmediato, id disponible

        // 3) Vaciar del carrito SOLO lo comprado (todo está dentro de la misma tx).
        carrito.borrarDelCarrito(usuarioId, comprados);

        return guardado;
    }

    /**
     * Guarda los datos de entrega en el perfil del usuario si pidió
     * "recordar mis datos" en el checkout (misma casilla del sistema original).
     */
    @Transactional
    public void recordarDatosEntrega(Integer usuarioId, String direccion, String telefono) {
        var u = usuarios.findById(usuarioId)
                .orElseThrow(() -> new CheckoutException("Usuario no encontrado."));
        if (direccion != null && !direccion.isBlank()) u.setDireccion(direccion.trim());
        if (telefono != null && !telefono.isBlank()) u.setTelefono(telefono.trim());
        usuarios.save(u);
    }
}
