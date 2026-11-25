package co.cabore.CaboreApplication.estabilizacion;

import co.cabore.CaboreApplication.model.Categoria;
import co.cabore.CaboreApplication.model.EstadoVerificacion;
import co.cabore.CaboreApplication.model.PedidoItem;
import co.cabore.CaboreApplication.model.Producto;
import co.cabore.CaboreApplication.model.Rutas;
import co.cabore.CaboreApplication.model.Tienda;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas unitarias (sin Spring ni base de datos) de las reglas de las entidades. */
class ModeloEstabilizacionTest {

    @Test
    void productoNuevoEsPendienteConCategoriaPorDefecto() {
        Producto p = new Producto();
        assertThat(p.getEstadoVerificacion()).isEqualTo(EstadoVerificacion.pendiente);
        assertThat(p.getCategoriaId()).isEqualTo(12);
        assertThat(p.isActivo()).isTrue();
        assertThat(p.isDisponible()).isFalse(); // pendiente + sin stock: no se puede comprar
    }

    @Test
    void preciosUsanBigDecimalSinErroresDeRedondeo() {
        Producto p = new Producto();
        p.setPrecio(new BigDecimal("0.30"));
        p.setPrecioOferta(new BigDecimal("0.10"));
        assertThat(p.isTieneOferta()).isTrue();
        assertThat(p.getPrecioActual()).isEqualByComparingTo("0.10");
        assertThat(p.getDescuento()).isEqualTo(67);

        p.setPrecioOferta(new BigDecimal("0.30")); // oferta igual al precio: no es oferta
        assertThat(p.isTieneOferta()).isFalse();
        assertThat(p.getPrecioActual()).isEqualByComparingTo("0.30");
        assertThat(p.getDescuento()).isZero();
    }

    @Test
    void rutaDeImagenAceptaFormatoPhpYNombreSuelto() {
        Producto p = new Producto();
        assertThat(p.getRutaImagen()).isNull();
        p.setImagen("img/productos/prod_1.jpg");
        assertThat(p.getRutaImagen()).isEqualTo("/img/productos/prod_1.jpg");
        p.setImagen("prod_2.jpg");
        assertThat(p.getRutaImagen()).isEqualTo("/img/productos/prod_2.jpg");
        assertThat(Rutas.publica("https://x.co/a.png", "productos")).isEqualTo("https://x.co/a.png");
    }

    @Test
    void itemDePedidoAntiguoUsaPrecioUnitarioComoRespaldo() {
        PedidoItem antiguo = new PedidoItem();
        antiguo.setCantidad(2);
        antiguo.setPrecioUnit(BigDecimal.ZERO);
        antiguo.setSubtotal(BigDecimal.ZERO);
        antiguo.setPrecioUnitario(new BigDecimal("45000.00"));
        assertThat(antiguo.getPrecioEfectivo()).isEqualByComparingTo("45000");
        assertThat(antiguo.getSubtotalEfectivo()).isEqualByComparingTo("90000");

        PedidoItem nuevo = new PedidoItem();
        nuevo.setCantidad(3);
        nuevo.setPrecioUnit(new BigDecimal("100.00"));
        nuevo.setPrecioUnitario(new BigDecimal("100.00"));
        nuevo.setSubtotal(new BigDecimal("300.00"));
        assertThat(nuevo.getPrecioEfectivo()).isEqualByComparingTo("100");
        assertThat(nuevo.getSubtotalEfectivo()).isEqualByComparingTo("300");
    }

    @Test
    void tiendaMantieneSincronizadasActivoYActiva() {
        Tienda t = new Tienda();
        assertThat(t.isActivo()).isTrue();
        assertThat(t.isActiva()).isTrue();
        t.setActivo(false);
        assertThat(t.isActivo()).isFalse();
        assertThat(t.isActiva()).isFalse();
        t.setActivo(true);
        assertThat(t.isActiva()).isTrue();
    }

    @Test
    void categoriaReemplazaIconoCorruptoOVacio() {
        Categoria c = new Categoria();
        assertThat(c.getIconoSeguro()).isEqualTo(Categoria.ICONO_POR_DEFECTO);
        c.setIcono("?️");
        assertThat(c.getIconoSeguro()).isEqualTo(Categoria.ICONO_POR_DEFECTO);
        c.setIcono("💍");
        assertThat(c.getIconoSeguro()).isEqualTo("💍");
    }
}
