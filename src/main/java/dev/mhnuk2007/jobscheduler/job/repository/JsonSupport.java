package dev.mhnuk2007.jobscheduler.job.repository;

import tools.jackson.databind.json.JsonMapper;

public class JsonSupport {
    private JsonSupport() {}

    static final JsonMapper MAPPER = JsonMapper.builder().build();
}
