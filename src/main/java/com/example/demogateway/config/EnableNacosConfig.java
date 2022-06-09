package com.example.demogateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({SfconfigProperties.class})
public class EnableNacosConfig {
}
