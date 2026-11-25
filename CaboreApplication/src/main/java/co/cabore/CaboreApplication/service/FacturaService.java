package co.cabore.CaboreApplication.service;

import co.cabore.CaboreApplication.model.Pedido;
import co.cabore.CaboreApplication.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Facturas (fase 3): al igual que el sistema original, la factura es la
 * representación formal del pedido ya pagado/contra entrega; se numera a
 * partir del id del pedido (FAC-000123) para no duplicar información en la
 * base de datos. El cliente solo puede abrir la factura de SU pedido:
 * la consulta filtra por usuario_id autenticado.
 */
@Service
@RequiredArgsConstructor
public class FacturaService {

    private final PedidoRepository pedidos;

    /** Datos completos de la factura del usuario autenticado. */
    @Transactional(readOnly = true)
    public Pedido factura(Integer pedidoId, Integer usuarioId) {
        return pedidos.buscarDetalle(pedidoId, usuarioId)
                .orElseThrow(() -> new NoSuchElementException(
                        "La factura no existe o no pertenece a tu cuenta."));
    }
}
