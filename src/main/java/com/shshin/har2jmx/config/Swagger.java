package com.shshin.har2jmx.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class Swagger {

    @Bean
    public OpenAPI openAPI() {
        Info info = new Info()
                .title("Har2Jmx Swagger Doc")
                .version("v0.0.1")
                .description("Swagger 연습용 게시판 API 명세서입니다.");
        return new OpenAPI()
                .components(new Components())
                .info(info);
    }
//    @Bean
//    public OpenAPI openAPI() {
//        return new OpenAPI()
//                .info(info);
//    }
//
//    Info info = new Info().title("Har2Jmx Swagger Doc").version("0.0.1").description(
//            "<h3>Swagger test</h3>");
}
