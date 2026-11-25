package co.cabore.CaboreApplication.service;

import co.cabore.CaboreApplication.model.Favorito;
import co.cabore.CaboreApplication.model.Producto;
import co.cabore.CaboreApplication.model.Usuario;
import co.cabore.CaboreApplication.repository.FavoritoRepository;
import co.cabore.CaboreApplication.repository.ProductoRepository;
import co.cabore.CaboreApplication.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Favoritos PERSISTENTES (fase 3): se guardan en la tabla favoritos de MariaDB
 * con clave (usuario_id, producto_id). Sobreviven al cerrar sesión y cada
 * usuario solo ve y modifica los suyos.
 */
@Service
@RequiredArgsConstructor
public class FavoritoService {

    private final FavoritoRepository favoritos;
    private final ProductoRepository productos;
    private final UsuarioRepository usuarios;

    /** Alterna el estado: agrega si no estaba, quita si estaba. Devuelve el nuevo estado. */
    @Transactional
    public boolean alternar(Integer usuarioId, Long productoId) {
        Favorito existente = favoritos.findByUsuarioIdAndProductoId(usuarioId, productoId).orElse(null);
        if (existente != null) {
            favoritos.delete(existente);
            return false;
        }
        Producto p = productos.findById(productoId)
                .orElseThrow(() -> new NoSuchElementException("Producto no encontrado."));
        Usuario u = usuarios.getReferenceById(usuarioId);
        favoritos.save(new Favorito(u, p));
        return true;
    }

    @Transactional
    public void quitar(Integer usuarioId, Long productoId) {
        favoritos.findByUsuarioIdAndProductoId(usuarioId, productoId)
                .ifPresent(favoritos::delete);
    }

    @Transactional(readOnly = true)
    public List<Favorito> listar(Integer usuarioId) {
        return favoritos.findByUsuarioIdOrderByGuardadoEnDesc(usuarioId);
    }

    /** Ids de productos favoritos del usuario: alimenta los corazones del catálogo. */
    @Transactional(readOnly = true)
    public Set<Long> idsFavoritos(Integer usuarioId) {
        return favoritos.idsProductosFavoritos(usuarioId).stream().collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public boolean esFavorito(Integer usuarioId, Long productoId) {
        return favoritos.existsByUsuarioIdAndProductoId(usuarioId, productoId);
    }
}
