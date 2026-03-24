package com.deskit.deskit.common.config;

import io.openvidu.java.client.OpenVidu;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenViduConfig {

    @Value("${openvidu.url}")
    private String openViduUrl;

    @Value("${openvidu.secret}")
    private String openViduSecret;

    @Value("${openvidu.http.connect-timeout-ms:2000}")
    private int connectTimeoutMs;

    @Value("${openvidu.http.response-timeout-ms:5000}")
    private int responseTimeoutMs;

    @Bean
    public OpenVidu openVidu() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(responseTimeoutMs))
                .build();

        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig);

        return new OpenVidu(openViduUrl, openViduSecret, httpClientBuilder);
    }
}
