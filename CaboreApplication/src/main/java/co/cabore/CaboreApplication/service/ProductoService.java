package co.cabore.CaboreApplication.service;

import co.cabore.CaboreApplication.model.Producto;
import co.cabore.CaboreApplication.repository.CarritoItemRepository;
import co.cabore.CaboreApplication.repository.PedidoItemRepository;
import co.cabore.CaboreApplication.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operaciones de producto que protegen el historial.
 *
 * pedido_items.producto_id tiene ON DELETE CASCADE en MariaDB: borrar un producto
 * que ya fue comprado eliminaría también sus líneas de pedido y dejaría facturas
 * incompletas. Por eso, si el producto aparece en algún pedido se ARCHIVA
 * (activo = false, destacado = false) en lugar de borrarse.
 */
@Service
@RequiredArgsConstructor
public class ProductoService {

    public enum ResultadoEliminacion { ELIMINADO, ARCHIVADO }

    private final ProductoRepository productos;
    private final PedidoItemRepository pedidoItems;
    private final CarritoItemRepository carrito;

    @Transactional
    public ResultadoEliminacion eliminar(Producto producto) {
        Long id = producto.getId();
        carrito.deleteByProductoId(id); // nunca dejar filas huérfanas en carritos
        if (pedidoItems.existsByProductoId(id)) {
            producto.setActivo(false);
            producto.setDestacado(false);
            productos.save(producto);
            return ResultadoEliminacion.ARCHIVADO;
        }
        productos.delete(producto);
        return ResultadoEliminacion.ELIMINADO;
    }
}
