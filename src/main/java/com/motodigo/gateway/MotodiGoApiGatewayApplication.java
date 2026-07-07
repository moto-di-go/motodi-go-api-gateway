package com.motodigo.gateway;

import com.motodigo.gateway.config.GatewayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
public class MotodiGoApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(MotodiGoApiGatewayApplication.class, args);
	}

}
