package org.slobben.cellserviceload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CellServiceLoadApplication {

    static void main(String[] args) {
        SpringApplication.run(CellServiceLoadApplication.class, args);
    }

}
