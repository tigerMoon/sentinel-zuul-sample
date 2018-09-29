package hello;

import coke.CokeApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.health.OrderedHealthAggregator;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.eureka.EurekaHealthCheckHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@SpringBootApplication
@RestController
@EnableDiscoveryClient
public class BookApplication {

    @Autowired
    private DiscoveryClient discoveryClient;

    @RequestMapping(value = "/book", method = RequestMethod.GET)
    public String book() {
        return "《Hello World》";
    }

    @RequestMapping(value = "/book/people", method = RequestMethod.GET)
    public String foo(@RequestParam String p) {
        return "《people`s world》";
    }

    @RequestMapping(value = "/block", method = RequestMethod.GET)
    public String block(@RequestParam(required = false) String p) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "《Block World》";
    }

    @RequestMapping(value = "/exception", method = RequestMethod.GET)
    public String except() {
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis % 1000 == 1 || currentTimeMillis % 1000 == 2 || currentTimeMillis % 1000 == 3) {
            return "exception";
        } else {
            throw new RuntimeException();
        }
    }

    @Bean
    public EurekaHealthCheckHandler getBookHandler() {
        return new EurekaHealthCheckHandler(new OrderedHealthAggregator());
    }

    @RequestMapping("/service-instances/{applicationName}")
    public List<ServiceInstance> serviceInstancesByApplicationNameBook(@PathVariable String applicationName) {
        return this.discoveryClient.getInstances(applicationName);
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(CokeApplication.class)
                .properties("server.port=9081", "spring.application.name=book")
                .run(args);
    }
}
