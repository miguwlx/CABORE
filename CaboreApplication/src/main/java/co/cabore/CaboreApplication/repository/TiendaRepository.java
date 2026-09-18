package co.cabore.CaboreApplication.repository;

import co.cabore.CaboreApplication.model.Tienda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TiendaRepository extends JpaRepository<Tienda, Integer> {

    Optional<Tienda> findByUsuarioId(Integer usuarioId);

    List<Tienda> findByActivoTrue();
}