package co.cabore.CaboreApplication.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

/**
 * Categoría de producto (tabla categorias de cabore.sql).
 *
 * El DEFAULT de categorias.icono en el dump quedó corrupto ('?️') porque el
 * emoji se exportó sin utf8mb4. Aquí el valor por defecto es 🏷️ y getIconoSeguro()
 * reemplaza cualquier icono vacío o corrupto. Ver db/correcciones-opcionales.sql.
 */
@Entity
@Table(name = "categorias")
@Getter
@Setter
public class Categoria {

    public static final String ICONO_POR_DEFECTO = "🏷️";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(length = 10)
    private String icono = ICONO_POR_DEFECTO;

    public Categoria() {}

    @Transient
    public String getIconoSeguro() {
        if (icono == null || icono.isBlank() || icono.startsWith("?")) return ICONO_POR_DEFECTO;
        return icono;
    }
}
