package com.example.CampusLink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class YoutubeConfig {

    @Bean
    public RestClient youtubeRestClient() {

        return RestClient.builder()
                .baseUrl("https://www.googleapis.com/youtube/v3")
                .build();
    }
}
