package co.cabore.CaboreApplication.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Producto guardado en el carrito de compras de un usuario (tabla carrito_items).
 * El carrito ahora es PERSISTENTE: sobrevive al cerrar sesión o cambiar de
 * dispositivo, y está atado SIEMPRE al usuario autenticado (nunca al navegador),
 * corrigiendo el viejo problema de sesiones compartidas del PHP original.
 *
 * Su DDL vive en src/main/resources/db/fase3-carrito.sql (CREATE TABLE IF NOT EXISTS),
 * ejecutado automáticamente al arrancar la aplicación.
 */
@Entity
@Table(name = "carrito_items")
@Getter
@Setter
public class CarritoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private Integer cantidad = 1;

    @CreationTimestamp
    @Column(name = "agregado_en", updatable = false)
    private LocalDateTime agregadoEn;

    public CarritoItem() {}
}
