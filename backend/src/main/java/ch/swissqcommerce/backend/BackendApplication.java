package ch.swissqcommerce.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableScheduling // Enables schedules like the 8s OLAP ETL sync replication loop
@EnableAspectJAutoProxy
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
