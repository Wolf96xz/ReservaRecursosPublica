package io.github.aburgos.reservas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NotificacionesApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificacionesApplication.class, args);
    }
}
