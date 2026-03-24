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
                        .summary("REST API for projects, estimates, and AI-assisted PDF import.")
                        .description("""
                                ## Overview

                                **Aether App** exposes a REST API that:

                                1. Accepts **PDF estimate** uploads via `multipart/form-data`.
                                2. Stores the PDF in **Google Cloud Storage** and a record in **Firestore**.
                                3. Publishes a **Pub/Sub event** so the **Project PDF Sync** agent (Aether AI) can import tasks and offers into the project.
                                4. Returns an immediate **acknowledgment** — processing is asynchronous.

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
                                .name("Estimate / PDF processing")
                                .description("PDF uploads, pricing requests, and related estimate endpoints. " +
                                        "Pub/Sub triggers the Project PDF Sync agent for line-item import."),
                        new Tag()
                                .name("System")
                                .description("Root and health-check endpoints.")
                ));
    }
}
