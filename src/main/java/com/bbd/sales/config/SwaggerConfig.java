package com.bbd.sales.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("sales API")
                        .description("sales Application API Documentation")
                        .version("v1.0"))

                .addServersItem(new Server()
                        .url("http://localhost:8082/sales")
                        .description("local에서 직접 띄울 때"))

                .addServersItem(new Server()
                        .url("http://192.168.200.220/sales")
                        .description("강의실 노트북"))

                .addServersItem(new Server()
                        .url("https://bbd.inwoohub.com/sales")
                        .description("ECS"))

                .addServersItem(new Server()
                        .url("http://100.73.142.41/sales")
                        .description("TailScale 강의실 노트북"))

                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name("Authorization")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))

                .addSecurityItem(new SecurityRequirement()
                        .addList(SECURITY_SCHEME_NAME));


    }
}