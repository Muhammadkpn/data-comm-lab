package com.learn.datacomm.cors;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Demo CORS (bab 11 materi). Satu aplikasi, DUA port:
 *
 *   http://localhost:8086  "frontend" -- buka halaman ini di browser
 *   http://localhost:8085  "API"      -- dipanggil oleh halaman di atas
 *
 * Origin = skema + host + PORT. Beda port saja sudah cukup membuat browser menerapkan
 * same-origin policy, jadi kita tidak butuh dua domain untuk mencobanya.
 */
@SpringBootApplication
public class CorsDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CorsDemoApplication.class, args);
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> frontendPort(
            @Value("${cors.frontend-port}") int frontendPort) {
        return factory -> {
            if (frontendPort > 0) {
                Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
                connector.setPort(frontendPort);
                factory.addAdditionalTomcatConnectors(connector);
            }
        };
    }
}
