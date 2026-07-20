package cn.fj.roadagent.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "cn.fj.roadagent")
public class RoadAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(RoadAgentApplication.class, args);
    }
}
