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
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ítem de un pedido (tabla pedido_items de cabore.sql).
 *
 * NOTA DE COMPATIBILIDAD: la tabla tiene dos columnas de precio unitario por
 * la evolución del sistema legacy: precio_unit (Laravel) y precio_unitario
 * (facturas del PHP original). Los pedidos antiguos tienen precio_unit = 0 y
 * subtotal = 0, con el precio real en precio_unitario. Por eso las vistas y
 * reportes usan getPrecioEfectivo() / getSubtotalEfectivo(), que toman el
 * primer valor mayor que cero. El checkout nuevo rellena ambas columnas.
 */
@Entity
@Table(name = "pedido_items")
@Getter
@Setter
public class PedidoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private Integer cantidad = 1;

    @Column(name = "precio_unit", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario = BigDecimal.ZERO;

    public PedidoItem() {}

    /** Precio unitario real: precio_unit si es > 0; si no, precio_unitario (pedidos antiguos). */
    @Transient
    public BigDecimal getPrecioEfectivo() {
        if (precioUnit != null && precioUnit.signum() > 0) return precioUnit;
        return precioUnitario == null ? BigDecimal.ZERO : precioUnitario;
    }

    /** Subtotal real: subtotal si es > 0; si no, cantidad × precio efectivo. */
    @Transient
    public BigDecimal getSubtotalEfectivo() {
        if (subtotal != null && subtotal.signum() > 0) return subtotal;
        int c = cantidad == null ? 0 : cantidad;
        return getPrecioEfectivo().multiply(BigDecimal.valueOf(c)).setScale(2, RoundingMode.HALF_UP);
    }
}
