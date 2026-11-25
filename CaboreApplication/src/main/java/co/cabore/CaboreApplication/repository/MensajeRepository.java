package co.cabore.CaboreApplication.repository;

import co.cabore.CaboreApplication.model.Mensaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MensajeRepository extends JpaRepository<Mensaje, Integer> {

    List<Mensaje> findByConversacionIdOrderByIdAsc(Integer conversacionId);

    Optional<Mensaje> findFirstByConversacionIdOrderByIdDesc(Integer conversacionId);

    @Query("""
            select count(m) from Mensaje m
            where m.conversacion.id = :conversacionId
              and m.emisor.id <> :usuarioId
              and m.leido = false
            """)
    long contarNoLeidosEnConversacion(@Param("conversacionId") Integer conversacionId,
                                      @Param("usuarioId") Integer usuarioId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Mensaje m set m.leido = true
            where m.conversacion.id = :conversacionId
              and m.emisor.id <> :usuarioId
              and m.leido = false
            """)
    int marcarComoLeidos(@Param("conversacionId") Integer conversacionId,
                         @Param("usuarioId") Integer usuarioId);

    @Query("""
            select count(m) from Mensaje m
            where m.conversacion.tienda.id = :tiendaId
              and m.emisor.id <> :usuarioId
              and m.leido = false
            """)
    long contarNoLeidosTienda(@Param("tiendaId") Integer tiendaId, @Param("usuarioId") Integer usuarioId);

    @Query("""
            select count(m) from Mensaje m
            where m.conversacion.cliente.id = :clienteId
              and m.emisor.id <> :clienteId
              and m.leido = false
            """)
    long contarNoLeidosCliente(@Param("clienteId") Integer clienteId);
}
