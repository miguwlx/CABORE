package co.cabore.CaboreApplication.repository;

import co.cabore.CaboreApplication.model.Rol;
import co.cabore.CaboreApplication.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByCorreo(String correo);

    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    Optional<Usuario> findByResetToken(String resetToken);

    Optional<Usuario> findByGoogleId(String googleId);

    boolean existsByCorreo(String correo);

    boolean existsByCorreoIgnoreCase(String correo);

    long countByRol(Rol rol);

    @Query("""
            select u from Usuario u
            where (:rol is null or u.rol = :rol)
              and (:q = '' or lower(u.nombre) like lower(concat('%', :q, '%'))
                   or lower(u.correo) like lower(concat('%', :q, '%')))
            order by u.id desc
            """)
    List<Usuario> buscarAdmin(@Param("rol") Rol rol, @Param("q") String q);
}