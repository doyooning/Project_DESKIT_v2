package com.deskit.deskit.common.config;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeZoneConfig {
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

    @Bean
    public HibernatePropertiesCustomizer hibernateTimezoneCustomizer() {
        return (properties) -> properties.put("hibernate.jdbc.time_zone", "Asia/Seoul");
    }
}