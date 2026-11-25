package co.cabore.CaboreApplication.model;

/**
 * Estado de verificación de un producto (columna productos.estado_verificacion).
 * Coincide exactamente con el ENUM de MariaDB definido en cabore.sql.
 */
public enum EstadoVerificacion {
    pendiente, aprobado, rechazado
}
