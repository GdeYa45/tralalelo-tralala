package ru.itis.documents.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Этап 8.3 (P0): Swagger/OpenAPI.
 *
 * DoD: swagger открывается, видны схемы DTO.
 *
 * springdoc-openapi-starter-webmvc-ui уже подключён в pom.xml.
 * Этот конфиг добавляет метаданные в OpenAPI (заголовок/описание),
 * чтобы в Swagger UI было понятно, что это за API.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI semWork3OpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SemWork3 Plant Care API")
                        .version("v1")
                        .description("REST API для проекта семестровой работы №3 (уход за растениями)")
                        .contact(new Contact().name("ITIS").url("https://itis.kpfu.ru"))
                        .license(new License().name("MIT")))
                .externalDocs(new ExternalDocumentation()
                        .description("Swagger UI")
                        .url("/swagger-ui/index.html"));
    }
}