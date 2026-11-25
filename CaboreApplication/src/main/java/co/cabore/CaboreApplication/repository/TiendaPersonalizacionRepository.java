package co.cabore.CaboreApplication.repository;

import co.cabore.CaboreApplication.model.TiendaPersonalizacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TiendaPersonalizacionRepository extends JpaRepository<TiendaPersonalizacion, Integer> {

    Optional<TiendaPersonalizacion> findByTiendaId(Integer tiendaId);
}
