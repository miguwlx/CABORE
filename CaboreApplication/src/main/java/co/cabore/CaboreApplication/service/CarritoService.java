package co.cabore.CaboreApplication.service;

import co.cabore.CaboreApplication.model.CarritoItem;
import co.cabore.CaboreApplication.model.Producto;
import co.cabore.CaboreApplication.repository.CarritoItemRepository;
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
 * Carrito de compras PERSISTENTE (fase 3).
 *
 * En el sistema legacy el carrito vivía en la sesión PHP; aquí se guarda en la
 * tabla carrito_items de MariaDB, atado al usuario autenticado, de modo que
 * sobrevive a cierres de sesión y cambios de dispositivo sin mezclarse entre
 * cuentas. El precio y la disponibilidad SIEMPRE se leen de la base de datos
 * al momento de listar o comprar; el navegador nunca envía precios.
 */
@Service
@RequiredArgsConstructor
public class CarritoService {

    private final CarritoItemRepository carrito;
    private final ProductoRepository productos;
    private final UsuarioRepository usuarios;

    /** Fila ya preparada para la vista (precio vigente tomado de la BD). */
    public record ItemCarrito(
            Long productoId,
            String nombre,
            BigDecimal precioUnit,
            String imagen,
            String tienda,
            int cantidad,
            int stock,
            BigDecimal subtotal) {}

    /** Contenido + totales calculados en el servidor. */
    public record ResumenCarrito(List<ItemCarrito> items, int count, BigDecimal total, boolean ajustado) {}

    /**
     * Lista el carrito aplicando las mismas reglas de saneamiento del legacy:
     * si un producto se desactivó, se rechazó en verificación o se quedó sin
     * stock, se retira; si el stock bajó por debajo de la cantidad pedida, se
     * ajusta. El flag `ajustado` permite avisar al usuario en el toast.
     */
    @Transactional
    public ResumenCarrito ver(Integer usuarioId) {
        boolean ajustado = false;
        List<ItemCarrito> items = new ArrayList<>();
        for (CarritoItem fila : carrito.findByUsuarioIdOrderByIdAsc(usuarioId)) {
            Producto p = fila.getProducto();
            if (p == null || !p.isDisponible() || p.getTienda() == null || !p.getTienda().isActivo()) {
                carrito.delete(fila);
                ajustado = true;
                continue;
            }
            if (fila.getCantidad() > p.getStock()) {
                fila.setCantidad(p.getStock());
                ajustado = true;
            }
            if (fila.getCantidad() <= 0) {
                carrito.delete(fila);
                ajustado = true;
                continue;
            }
            BigDecimal precio = p.getPrecioActual().setScale(2, RoundingMode.HALF_UP);
            items.add(new ItemCarrito(
                    p.getId(),
                    p.getNombre(),
                    precio,
                    p.getRutaImagen(),
                    p.getTienda().getNombre(),
                    fila.getCantidad(),
                    p.getStock(),
                    precio.multiply(BigDecimal.valueOf(fila.getCantidad())).setScale(2, RoundingMode.HALF_UP)));
        }
        BigDecimal total = items.stream().map(ItemCarrito::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        return new ResumenCarrito(items, items.size(), total, ajustado);
    }

    /** Agrega un producto (o incrementa la cantidad existente) respetando el stock real. */
    @Transactional
    public void agregar(Integer usuarioId, Long productoId, int cantidad) {
        if (cantidad <= 0) cantidad = 1;
        Producto p = productos.findById(productoId)
                .orElseThrow(() -> new CheckoutException("El producto ya no existe."));
        if (!p.isDisponible() || p.getTienda() == null || !p.getTienda().isActivo()) {
            throw new CheckoutException("«" + p.getNombre() + "» no está disponible en este momento.");
        }
        CarritoItem fila = carrito.findByUsuarioIdAndProductoId(usuarioId, productoId)
                .orElseGet(() -> {
                    CarritoItem nuevo = new CarritoItem();
                    nuevo.setUsuario(usuarios.getReferenceById(usuarioId));
                    nuevo.setProducto(p);
                    nuevo.setCantidad(0);
                    return nuevo;
                });
        int proyectada = fila.getCantidad() + cantidad;
        if (proyectada > p.getStock()) {
            proyectada = p.getStock();
        }
        if (proyectada <= 0) {
            throw new CheckoutException("No hay stock suficiente de «" + p.getNombre() + "».");
        }
        fila.setCantidad(proyectada);
        carrito.save(fila);
    }

    /** Fija la cantidad de un producto; con 0 o menos se elimina la fila. */
    @Transactional
    public void ajustar(Integer usuarioId, Long productoId, int nuevaCantidad) {
        CarritoItem fila = carrito.findByUsuarioIdAndProductoId(usuarioId, productoId).orElse(null);
        if (fila == null) return;
        if (nuevaCantidad <= 0) {
            carrito.delete(fila);
            return;
        }
        Producto p = fila.getProducto();
        if (p != null && p.getStock() != null && nuevaCantidad > p.getStock()) {
            nuevaCantidad = p.getStock();
        }
        fila.setCantidad(nuevaCantidad);
        carrito.save(fila);
    }

    @Transactional
    public void eliminar(Integer usuarioId, Long productoId) {
        carrito.deleteByUsuarioIdAndProductoId(usuarioId, productoId);
    }

    @Transactional
    public void vaciar(Integer usuarioId) {
        carrito.deleteByUsuarioId(usuarioId);
    }

    /** Cantidad de productos distintos en el carrito (para la insignia de la barra). */
    @Transactional(readOnly = true)
    public long contar(Integer usuarioId) {
        return carrito.countByUsuarioId(usuarioId);
    }

    /** Filas crudas del carrito (para el checkout transaccional). */
    @Transactional(readOnly = true)
    public List<CarritoItem> filas(Integer usuarioId) {
        return carrito.findByUsuarioIdOrderByIdAsc(usuarioId);
    }


}
