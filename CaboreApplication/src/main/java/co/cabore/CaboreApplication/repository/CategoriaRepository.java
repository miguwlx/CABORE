package co.cabore.CaboreApplication.repository;

import co.cabore.CaboreApplication.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {

    List<Categoria> findAllByOrderByIdAsc();

    List<Categoria> findAllByOrderByNombreAsc();
}
