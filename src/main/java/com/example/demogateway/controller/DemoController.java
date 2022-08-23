package com.example.demogateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RefreshScope
public class DemoController {
	@Value("key1")
	private String key1;

	@GetMapping("/nacos/config")
	public String test() {
		return "key1:" + key1;
	}
}
