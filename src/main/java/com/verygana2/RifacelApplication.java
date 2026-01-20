package com.verygana2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.verygana2.config.RsaKeyProperties;

@SpringBootApplication
@EnableConfigurationProperties(RsaKeyProperties.class)
@EnableCaching
@EnableAsync
@EnableScheduling
public class RifacelApplication {

	public static void main(String[] args) {
		SpringApplication.run(RifacelApplication.class, args);
	}

}
