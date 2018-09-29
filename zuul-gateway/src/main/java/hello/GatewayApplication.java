package hello;

import hello.filter.SentinelServiceZuulFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.health.OrderedHealthAggregator;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EurekaHealthCheckHandler;
import org.springframework.cloud.netflix.hystrix.dashboard.EnableHystrixDashboard;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.cloud.netflix.ribbon.support.RibbonRequestCustomizer;
import org.springframework.cloud.netflix.turbine.EnableTurbine;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.netflix.zuul.filters.ProxyRequestHelper;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommandFactory;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonRoutingFilter;
import org.springframework.cloud.netflix.zuul.filters.route.ZuulFallbackProvider;
import org.springframework.cloud.netflix.zuul.filters.route.apache.HttpClientRibbonCommandFactory;
import org.springframework.context.annotation.Bean;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@EnableZuulProxy
@SpringBootApplication
@EnableTurbine
@EnableHystrixDashboard
public class GatewayApplication {

    private List<RibbonRequestCustomizer> requestCustomizers = Collections.emptyList();

    private Set<ZuulFallbackProvider> zuulFallbackProviders = Collections.emptySet();

    @Bean
    public EurekaHealthCheckHandler getHandler(){
        return new EurekaHealthCheckHandler(new OrderedHealthAggregator());
    }

    @Bean
    public RibbonRoutingFilter sentinelServiceZuulFilter(ProxyRequestHelper helper,SpringClientFactory clientFactory,
                                                               ZuulProperties zuulProperties){
        RibbonRoutingFilter filter = new SentinelServiceZuulFilter(helper, ribbonCommandFactory(clientFactory,zuulProperties),
                this.requestCustomizers);
        return filter;
    }

    private RibbonCommandFactory<?> ribbonCommandFactory(
            SpringClientFactory clientFactory, ZuulProperties zuulProperties) {
        return new HttpClientRibbonCommandFactory(clientFactory, zuulProperties, zuulFallbackProviders);
    }

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
