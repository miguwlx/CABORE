package co.cabore.CaboreApplication.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Expone públicamente /img/** buscando primero en la carpeta de subidas de
 * Spring Boot (cabore.uploads.dir), luego en la carpeta img/ de la raíz del
 * proyecto PHP original y finalmente en classpath:/static/img/.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String uploadsDir;

    public WebConfig(@Value("${cabore.uploads.dir:uploads/img}") String uploadsDir) {
        this.uploadsDir = uploadsDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadsDir).toAbsolutePath().normalize();
        Path phpRootImg = Paths.get("..", "img").toAbsolutePath().normalize();
        Path localImg = Paths.get("img").toAbsolutePath().normalize();

        registry.addResourceHandler("/img/**")
                .addResourceLocations(
                        uploadPath.toUri().toString() + "/",
                        phpRootImg.toUri().toString() + "/",
                        localImg.toUri().toString() + "/",
                        "classpath:/static/img/"
                );
    }
}
