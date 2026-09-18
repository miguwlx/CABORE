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
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tiendas")
@Getter
@Setter
public class Tienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    private String nombre = "Mi tienda";
    private String descripcion = "Descripción de mi tienda";
    private String color = "#c9a84c";
    private String plantilla = "moderna";
    private String logo;
    private String banner;
    private String ciudad;
    private String whatsapp;
    private String instagram;
    private boolean activo = true;
    private String motivoSuspension;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime creadoEn;

    @Column(insertable = false, updatable = false)
    private LocalDateTime actualizado;
}