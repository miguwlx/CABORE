package co.cabore.CaboreApplication.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Clave primaria compuesta de {@link Favorito}: (usuario, producto).
 */
public class FavoritoId implements Serializable {

    private Integer usuario;
    private Long producto;

    public FavoritoId() {}

    public FavoritoId(Integer usuario, Long producto) {
        this.usuario = usuario;
        this.producto = producto;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FavoritoId that)) return false;
        return Objects.equals(usuario, that.usuario) && Objects.equals(producto, that.producto);
    }

    @Override
    public int hashCode() {
        return Objects.hash(usuario, producto);
    }
}
