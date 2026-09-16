package cliente;

public class ClienteService {

    public int experienciaPorVerCatalogo(int experienciaActual) {
        return experienciaActual + 10;
    }

    public int experienciaPorCompra(int experienciaActual) {
        return experienciaActual + 50;
    }

    public int calcularNivel(int experiencia) {
        return (experiencia / 100) + 1;
    }

    public String procesarExperiencia(String accion, int experienciaActual) {

        if (accion.equals("catalogo")) {
            experienciaActual = experienciaPorVerCatalogo(experienciaActual);
        }

        if (accion.equals("compra")) {
            experienciaActual = experienciaPorCompra(experienciaActual);
        }

        int nivel = calcularNivel(experienciaActual);

        return experienciaActual + "," + nivel;
    }
}