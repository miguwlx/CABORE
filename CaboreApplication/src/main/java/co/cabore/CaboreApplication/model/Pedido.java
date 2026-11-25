package co.cabore.CaboreApplication.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedido realizado por un cliente (tabla pedidos de cabore.sql).
 */
@Entity
@Table(name = "pedidos")
@Getter
@Setter
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PedidoEstado estado = PedidoEstado.pendiente;

    @Column(columnDefinition = "text")
    private String direccion;

    @Column(name = "telefono_contacto", length = 30)
    private String telefonoContacto;

    @Column(columnDefinition = "text")
    private String notas;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PedidoItem> items = new ArrayList<>();

    public Pedido() {}

    /** Agrega un ítem manteniendo la relación en ambos sentidos. */
    public void addItem(PedidoItem item) {
        item.setPedido(this);
        items.add(item);
    }

    /** Número de factura derivado del id del pedido (formato legacy: FAC-000123). */
    public String getNumeroFactura() {
        return id == null ? "FAC-PREVIEW" : String.format("FAC-%06d", id);
    }
}
