package com.learn.datacomm.graphql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point server GraphQL. Jalankan class ini untuk start server di
 * http://localhost:8081 -- endpoint GraphQL ada di POST /graphql, dan
 * GraphiQL (UI untuk coba query manual di browser) ada di /graphiql.
 * Lihat resolver/ untuk Query & Mutation, dan schema.graphqls untuk
 * kontrak schema-nya.
 */
@SpringBootApplication
public class GraphqlDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphqlDemoApplication.class, args);
    }
}
