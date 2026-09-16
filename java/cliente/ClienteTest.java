package cliente;

public class ClienteTest {

    public static void main(String[] args) {

        ClienteService servicio = new ClienteService();

        if (args.length < 2) {
            System.out.println("Uso: java cliente.ClienteTest <accion> <experiencia>");
            return;
        }

        String accion = args[0];
        int experiencia = Integer.parseInt(args[1]);

        String resultado = servicio.procesarExperiencia(accion, experiencia);

        System.out.println(resultado);
    }
}