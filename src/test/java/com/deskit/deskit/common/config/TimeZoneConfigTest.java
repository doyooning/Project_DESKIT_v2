package com.deskit.deskit.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;

import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class TimeZoneConfigTest {

    @Test
    void initSetsDefaultTimeZoneToAsiaSeoul() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZoneConfig config = new TimeZoneConfig();

            config.init();

            assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Seoul");
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void hibernateCustomizerSetsJdbcTimeZone() {
        TimeZoneConfig config = new TimeZoneConfig();
        HibernatePropertiesCustomizer customizer = config.hibernateTimezoneCustomizer();
        Map<String, Object> properties = new HashMap<>();

        customizer.customize(properties);

        assertThat(properties).containsEntry("hibernate.jdbc.time_zone", "Asia/Seoul");
    }
}
