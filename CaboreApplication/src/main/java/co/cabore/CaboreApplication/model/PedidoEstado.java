package co.cabore.CaboreApplication.model;

/**
 * Estados posibles de un pedido (columna pedidos.estado).
 * Coincide con el ENUM de MariaDB: pendiente, confirmado, enviado, entregado, cancelado.
 */
public enum PedidoEstado {
    pendiente, confirmado, enviado, entregado, cancelado;

    /** Etiqueta legible para mostrar en pantalla. */
    public String etiqueta() {
        return switch (this) {
            case pendiente -> "Pendiente";
            case confirmado -> "Confirmado";
            case enviado -> "Enviado";
            case entregado -> "Entregado";
            case cancelado -> "Cancelado";
        };
    }
}
