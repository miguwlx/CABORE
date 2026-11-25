package co.cabore.CaboreApplication.service;

/**
 * Error de negocio durante el carrito o la compra (carrito vacío, sin stock,
 * datos incompletos). Lleva un mensaje apto para mostrar al cliente.
 */
public class CheckoutException extends RuntimeException {
    public CheckoutException(String mensaje) {
        super(mensaje);
    }
}
