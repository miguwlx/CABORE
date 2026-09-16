package cliente;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class ClienteApi {

    public static void main(String[] args) throws IOException {

        HttpServer servidor = HttpServer.create(
                new InetSocketAddress(8080), 0
        );

        servidor.createContext("/experiencia", ClienteApi::procesarExperiencia);

        servidor.setExecutor(null);
        servidor.start();

        System.out.println("Servidor Java de Cliente iniciado en http://localhost:8080");
    }

    private static void procesarExperiencia(HttpExchange intercambio) throws IOException {

        String consulta = intercambio.getRequestURI().getQuery();

        String accion = "catalogo";
        int experiencia = 0;

        if (consulta != null) {

            String[] parametros = consulta.split("&");

            for (String parametro : parametros) {

                String[] partes = parametro.split("=");

                if (partes.length == 2) {

                    if (partes[0].equals("accion")) {
                        accion = partes[1];
                    }

                    if (partes[0].equals("experiencia")) {
                        experiencia = Integer.parseInt(partes[1]);
                    }
                }
            }
        }

        ClienteService servicio = new ClienteService();

        String resultado = servicio.procesarExperiencia(
                accion,
                experiencia
        );

        String respuesta = "{\"resultado\":\"" + resultado + "\"}";

        intercambio.getResponseHeaders()
                .set("Content-Type", "application/json");

        intercambio.sendResponseHeaders(
                200,
                respuesta.getBytes().length
        );

        OutputStream salida = intercambio.getResponseBody();
        salida.write(respuesta.getBytes());
        salida.close();
    }
}