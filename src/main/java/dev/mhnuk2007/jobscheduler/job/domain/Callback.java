package dev.mhnuk2007.jobscheduler.job.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Callback {

    @Column(name = "callback_url", nullable = false)
    private String url;

    @Column(name = "callback_method", nullable = false)
    @Builder.Default
    private String method = "POST";

    @Column(name = "callback_headers", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = dev.mhnuk2007.jobscheduler.job.repository.JsonMapConverter.class)
    private Map<String, String> headers;

    @Column(name = "callback_body", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = dev.mhnuk2007.jobscheduler.job.repository.JsonObjectConverter.class)
    private Object body;
}