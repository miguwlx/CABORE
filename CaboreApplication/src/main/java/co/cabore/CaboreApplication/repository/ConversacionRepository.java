package co.cabore.CaboreApplication.repository;

import co.cabore.CaboreApplication.model.Conversacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversacionRepository extends JpaRepository<Conversacion, Integer> {

    List<Conversacion> findByClienteIdOrderByActualizadoEnDesc(Integer clienteId);

    List<Conversacion> findByTiendaIdOrderByActualizadoEnDesc(Integer tiendaId);

    Optional<Conversacion> findByTiendaIdAndClienteId(Integer tiendaId, Integer clienteId);
}
