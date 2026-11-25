package co.cabore.CaboreApplication.service;

/**
 * Operación válida en forma pero no permitida por el estado del dato o por el
 * rol/dueño (cancelar un pedido ya enviado, reseñar sin compra entregada, etc.).
 */
public class OperacionInvalidaException extends RuntimeException {
    public OperacionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
