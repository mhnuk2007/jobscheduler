package dev.mhnuk2007.jobscheduler.job.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    @GetMapping
    public String helloWorld() {
        return "Hello World!";
    }
}