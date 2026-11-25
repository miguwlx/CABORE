package co.cabore.CaboreApplication.service;

import co.cabore.CaboreApplication.model.Producto;
import co.cabore.CaboreApplication.model.Resena;
import co.cabore.CaboreApplication.model.Usuario;
import co.cabore.CaboreApplication.repository.ProductoRepository;
import co.cabore.CaboreApplication.repository.ResenaRepository;
import co.cabore.CaboreApplication.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Reseñas y calificaciones (fase 3) sobre la tabla resenas de MariaDB.
 *
 * Regla de negocio: un cliente solo puede opinar de productos que haya
 * comprado en un pedido ya ENTREGADO (una reseña por producto; si repite,
 * la suya se actualiza). Las reseñas alimentan las estrellas del catálogo.
 */
@Service
@RequiredArgsConstructor
public class ResenaService {

    private final ResenaRepository resenas;
    private final ProductoRepository productos;
    private final UsuarioRepository usuarios;

    @Transactional(readOnly = true)
    public List<Resena> porProducto(Long productoId) {
        return resenas.findByProductoIdOrderByCreadoEnDesc(productoId);
    }

    @Transactional(readOnly = true)
    public Optional<Resena> mia(Long productoId, Integer usuarioId) {
        return resenas.findByProductoIdAndUsuarioId(productoId, usuarioId);
    }

    @Transactional(readOnly = true)
    public boolean puedeResenar(Long productoId, Integer usuarioId) {
        return resenas.comprasEntregadas(productoId, usuarioId) > 0;
    }

    /** Crea o actualiza la reseña del usuario autenticado sobre un producto. */
    @Transactional
    public Resena publicar(Integer usuarioId, Long productoId, int calificacion, String comentario) {
        if (calificacion < 1 || calificacion > 5) {
            throw new OperacionInvalidaException("La calificación debe estar entre 1 y 5 estrellas.");
        }
        if (comentario == null || comentario.isBlank()) {
            throw new OperacionInvalidaException("Escribe un comentario para tu reseña.");
        }
        Producto p = productos.findById(productoId)
                .orElseThrow(() -> new NoSuchElementException("Producto no encontrado."));
        if (!puedeResenar(productoId, usuarioId)) {
            throw new OperacionInvalidaException(
                    "Solo puedes reseñar «" + p.getNombre() + "» cuando tu pedido haya sido entregado.");
        }

        Resena r = resenas.findByProductoIdAndUsuarioId(productoId, usuarioId).orElseGet(() -> {
            Resena nueva = new Resena();
            nueva.setProducto(p);
            nueva.setUsuario(usuarios.getReferenceById(usuarioId));
            return nueva;
        });
        r.setCalificacion(calificacion);
        r.setComentario(comentario.trim());
        return resenas.save(r);
    }

    /** El dueño (o un administrador) puede eliminar una reseña. */
    @Transactional
    public void eliminar(Integer usuarioId, Integer resenaId) {
        Resena r = resenas.findById(resenaId)
                .orElseThrow(() -> new NoSuchElementException("Reseña no encontrada."));
        if (!r.getUsuario().getId().equals(usuarioId)) {
            throw new OperacionInvalidaException("No puedes eliminar la reseña de otra persona.");
        }
        resenas.delete(r);
    }

    @Transactional(readOnly = true)
    public List<Resena> mias(Integer usuarioId) {
        return resenas.findByUsuarioIdOrderByCreadoEnDesc(usuarioId);
    }
}
