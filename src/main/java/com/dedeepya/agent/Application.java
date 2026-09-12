package com.dedeepya.agent;

import com.dedeepya.agent.infrastructure.mcp.PolicyServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class Application {
  public static void main(String[] args) throws Exception {
    if (args.length == 1 && args[0].equals("--mcp-stdio")) {
      PolicyServer.serve();
      return;
    }
    SpringApplication.run(Application.class, args);
  }
}
