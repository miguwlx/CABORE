package co.cabore.CaboreApplication.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tienda de un emprendedor (tabla tiendas).
 *
 * NOTA activo/activa: el esquema real tiene DOS columnas booleanas. "activa" es
 * la original y "activo" la agregó el módulo administrador (gestionar_tienda.php
 * y admin.php leen/escriben "activo"). La fuente de verdad es "activo"; al
 * cambiarla se sincroniza "activa" para que ambas columnas nunca diverjan.
 */
@Entity
@Table(name = "tiendas")
@Getter
@Setter
public class Tienda {

    public static final List<String> PLANTILLAS = List.of("moderna", "minimalista", "elegante", "colorida", "tecnologica");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    private String nombre = "Mi tienda";
    @Column(columnDefinition = "text")
    private String descripcion = "Descripción de mi tienda";
    private String color = "#c9a84c";
    private String plantilla = "moderna";
    private String logo;
    private String banner;
    private String ciudad;
    private String whatsapp;
    private String instagram;

    /** Columna principal de estado (usada por el módulo administrador). */
    private boolean activo = true;

    /** Columna heredada: se mantiene sincronizada con "activo". */
    @Setter(lombok.AccessLevel.NONE)
    private boolean activa = true;

    private String motivoSuspension;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime creadoEn;

    @Column(insertable = false, updatable = false)
    private LocalDateTime actualizado;

    public void setActivo(boolean activo) {
        this.activo = activo;
        this.activa = activo;
    }

    @Transient
    public String getRutaLogo() { return Rutas.publica(logo, "logos"); }

    @Transient
    public String getRutaBanner() { return Rutas.publica(banner, "banners"); }

    /** Plantilla validada contra las cuatro originales. */
    @Transient
    public String getPlantillaSegura() {
        return PLANTILLAS.contains(plantilla) ? plantilla : "moderna";
    }

    @Transient
    public String getWhatsappDigitos() {
        return whatsapp == null ? null : whatsapp.replaceAll("[^0-9]", "");
    }

    @Transient
    public String getInstagramUsuario() {
        if (instagram == null) return null;
        String i = instagram.trim();
        while (i.startsWith("@")) i = i.substring(1);
        return i.isBlank() ? null : i;
    }
}
