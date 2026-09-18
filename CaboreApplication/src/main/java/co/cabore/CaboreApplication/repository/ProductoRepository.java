package co.cabore.CaboreApplication.repository;

import co.cabore.CaboreApplication.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findByTiendaId(Integer tiendaId);
}