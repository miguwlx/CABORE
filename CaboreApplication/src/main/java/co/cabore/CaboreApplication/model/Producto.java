package co.cabore.CaboreApplication.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Producto del catálogo (tabla productos de cabore.sql).
 *
 * - El dinero se maneja con BigDecimal (columnas decimal(12,2)).
 * - Todo producto NUEVO nace en estado "pendiente" (igual que guardar_producto.php):
 *   solo es visible para clientes cuando un administrador lo aprueba.
 * - Categoría por defecto: 12 ("Otros"), igual que el DEFAULT de la columna.
 */
@Entity
@Table(name = "productos")
public class Producto {

    /** Categoría "Otros" del dump original (DEFAULT 12 en productos.categoria_id). */
    public static final int CATEGORIA_POR_DEFECTO = 12;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(columnDefinition = "text")
    private String descripcion;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio = BigDecimal.ZERO;

    @Column(name = "precio_oferta", precision = 12, scale = 2)
    private BigDecimal precioOferta;

    @Column(name = "categoria_id")
    private Integer categoriaId = CATEGORIA_POR_DEFECTO;

    private String imagen;

    private Integer stock = 0;

    /** 1 = aparece en las sugerencias "Destacados para ti". */
    private boolean destacado = false;

    private boolean activo = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_verificacion", nullable = false)
    private EstadoVerificacion estadoVerificacion = EstadoVerificacion.pendiente;

    @Column(name = "motivo_rechazo")
    private String motivoRechazo;

    @Column(name = "revisado_por")
    private Integer revisadoPor;

    @Column(name = "revisado_en")
    private LocalDateTime revisadoEn;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @Column(insertable = false, updatable = false)
    private LocalDateTime actualizado;

    @ManyToOne
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    /** Promedio de calificación (no es columna: se calcula desde resenas). */
    @Transient
    private Double ratingPromedio;

    /** Número de reseñas (no es columna: se calcula desde resenas). */
    @Transient
    private Integer totalResenas;

    public Producto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }

    public BigDecimal getPrecioOferta() { return precioOferta; }
    public void setPrecioOferta(BigDecimal precioOferta) { this.precioOferta = precioOferta; }

    public Integer getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Integer categoriaId) { this.categoriaId = categoriaId; }

    public String getImagen() { return imagen; }
    public void setImagen(String imagen) { this.imagen = imagen; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public boolean isDestacado() { return destacado; }
    public void setDestacado(boolean destacado) { this.destacado = destacado; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public EstadoVerificacion getEstadoVerificacion() { return estadoVerificacion; }
    public void setEstadoVerificacion(EstadoVerificacion estadoVerificacion) { this.estadoVerificacion = estadoVerificacion; }

    public String getMotivoRechazo() { return motivoRechazo; }
    public void setMotivoRechazo(String motivoRechazo) { this.motivoRechazo = motivoRechazo; }

    public Integer getRevisadoPor() { return revisadoPor; }
    public void setRevisadoPor(Integer revisadoPor) { this.revisadoPor = revisadoPor; }

    public LocalDateTime getRevisadoEn() { return revisadoEn; }
    public void setRevisadoEn(LocalDateTime revisadoEn) { this.revisadoEn = revisadoEn; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }

    public LocalDateTime getActualizado() { return actualizado; }

    public Tienda getTienda() { return tienda; }
    public void setTienda(Tienda tienda) { this.tienda = tienda; }

    public Double getRatingPromedio() { return ratingPromedio; }
    public void setRatingPromedio(Double ratingPromedio) { this.ratingPromedio = ratingPromedio; }

    public Integer getTotalResenas() { return totalResenas; }
    public void setTotalResenas(Integer totalResenas) { this.totalResenas = totalResenas; }

    /* ── Métodos derivados (reglas de negocio de visualización) ─────────── */

    /** El producto está visible y comprable en el catálogo del cliente. */
    @Transient
    public boolean isDisponible() {
        return activo
                && estadoVerificacion == EstadoVerificacion.aprobado
                && stock != null && stock > 0;
    }

    /** True si tiene un precio de oferta válido (menor que el precio normal). */
    @Transient
    public boolean isTieneOferta() {
        return precioOferta != null && precio != null
                && precioOferta.signum() > 0
                && precioOferta.compareTo(precio) < 0;
    }

    /** Precio vigente: oferta si existe y es menor, si no, precio normal. */
    @Transient
    public BigDecimal getPrecioActual() {
        if (isTieneOferta()) return precioOferta;
        return precio == null ? BigDecimal.ZERO : precio;
    }

    /** Porcentaje de descuento redondeado (0 si no hay oferta). */
    @Transient
    public int getDescuento() {
        if (!isTieneOferta() || precio.signum() == 0) return 0;
        BigDecimal ratio = precioOferta.divide(precio, 4, RoundingMode.HALF_UP);
        return BigDecimal.ONE.subtract(ratio).multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /**
     * Ruta pública de la imagen, o null si no tiene.
     * Compatible con las dos formas en que se guardó la columna:
     *  - PHP original: "img/productos/prod_xxx.jpg" (ruta relativa completa)
     *  - nombre suelto: "prod_xxx.jpg"
     */
    @Transient
    public String getRutaImagen() {
        return Rutas.publica(imagen, "productos");
    }
}
