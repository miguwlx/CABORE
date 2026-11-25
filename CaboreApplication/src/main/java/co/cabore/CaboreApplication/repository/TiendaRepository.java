package co.cabore.CaboreApplication.repository;

import co.cabore.CaboreApplication.model.Tienda;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TiendaRepository extends JpaRepository<Tienda, Integer> {

    Optional<Tienda> findByUsuarioId(Integer usuarioId);

    List<Tienda> findByActivoTrue();

    List<Tienda> findAllByOrderByNombreAsc();

    @EntityGraph(attributePaths = {"usuario"})
    @Query("""
            select t from Tienda t
            join t.usuario u
            where (:q = '' or lower(t.nombre) like lower(concat('%', :q, '%'))
                   or lower(u.nombre) like lower(concat('%', :q, '%'))
                   or lower(u.correo) like lower(concat('%', :q, '%')))
            order by t.id desc
            """)
    List<Tienda> buscarAdmin(@Param("q") String q);
}