package cn.fj.roadagent.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "cn.fj.roadagent")
@EnableScheduling
public class RoadAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(RoadAgentApplication.class, args);
    }
}
