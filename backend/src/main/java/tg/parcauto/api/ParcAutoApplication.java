package tg.parcauto.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@org.springframework.scheduling.annotation.EnableScheduling
public class ParcAutoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParcAutoApplication.class, args);
    }
}
