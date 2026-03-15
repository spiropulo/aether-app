package com.aether.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI aetherOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Aether App")
                        .summary("AI-powered PDF → GraphQL schema mapper built on Google ADK.")
                        .description("""
                                ## Overview

                                **Aether App** exposes a REST API that:

                                1. Accepts any **PDF document** via a `multipart/form-data` upload.
                                2. Stores the PDF durably in **Google Cloud Storage**.
                                3. Creates a tracking record in **Firestore** (file name, location, size, upload date, uploader).
                                4. Publishes a **Pub/Sub event** that triggers the AI agent asynchronously.
                                5. Returns an immediate **acknowledgment** — callers don't need to wait for processing.

                                The AI agent then parses the PDF, fetches the live GraphQL schema, maps every\s
                                field to the correct input types, and executes the required mutations in dependency order.

                                ## Swagger UI
                                Interactive docs are available at `/swagger-ui.html`.

                                ## Authentication
                                Pass `tenant_id` as a query parameter to scope all records to a tenant.\s
                                JWT authentication is supported via the `Authorization: Bearer <token>` header.
                                """)
                        .version("0.1.0")
                        .contact(new Contact()
                                .name("Aether App")
                                .url("http://localhost:8080/"))
                        .license(new License().name("Proprietary")))
                .tags(List.of(
                        new Tag()
                                .name("Schema Mapper Agent")
                                .description("Upload a PDF document and let the AI agent parse its contents, " +
                                        "map every field to the GraphQL schema, execute the required mutations, " +
                                        "and return a full structured report."),
                        new Tag()
                                .name("System")
                                .description("Root and health-check endpoints.")
                ));
    }
}
