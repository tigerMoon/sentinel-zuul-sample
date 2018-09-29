package hello;

import org.springframework.boot.actuate.health.OrderedHealthAggregator;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.netflix.eureka.EurekaHealthCheckHandler;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
import org.springframework.context.annotation.Bean;

@EnableEurekaServer
@SpringBootApplication
public class EurekaServiceApplication {


    @Bean
    public EurekaHealthCheckHandler getHandler(){
        return new EurekaHealthCheckHandler(new OrderedHealthAggregator());
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(EurekaServiceApplication.class).web(true).run(args);
    }
}
