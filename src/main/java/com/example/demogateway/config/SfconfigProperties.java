package com.example.demogateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sfconfig")
public class SfconfigProperties {
    private String name;

}
