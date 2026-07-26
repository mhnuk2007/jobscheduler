package dev.mhnuk2007.jobscheduler;

import org.springframework.boot.SpringApplication;

public class TestJobSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.from(JobSchedulerApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
