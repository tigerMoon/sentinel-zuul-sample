# sentinel-zuul-sample
this is simple project for alibaba/Sentinel spring cloud zuul integration sample.

Sentinel can provide `ServiceId` level and `API PATH` level for zuul flow control. 

*Note*  
this project is for zuul1.

## Modules

**eureka-server**:

this module used for discovery server. when service restart. it should be restart.

**zuul-backend**

backend service after gateway. consist of two application book and coke.

**zuul-gateway**

zuul gateway application


## How to run

run as spring boot application

```
curl -i localhost:8990/coke/coke

curl -i localhost:8990/coke/block

curl -i localhost:8990/coke/except

curl -i localhost:8990/book/coke

```

## Integration Sentinel Filter

- `SentinelRibbonFilter`: extends `RibbonRoutingFilter`. override run method which add sentinel check. this project also enable **Hystrix Circuit**. 
- `SentinelOkHttpRoutingFilter`:  use okHttp3 as custom routing http client. and without **Hystrix** bind.

By default use `SentinelOkHttpRoutingFilter` as route filter:

```java
 // disable filter can be set here.
 public static void main(String[] args) {
        new SpringApplicationBuilder(GatewayApplication.class)
                .properties("zuul.RibbonRoutingFilter.route.disable=true",
                        "zuul.SentinelRibbonFilter.route.disable=true")
                .run(args);
    }
    
```


filters create structure like:

```
curl http://localhost:18990/tree?type=root

EntranceNode: machine-root(t:3 pq:0 bq:0 tq:0 rt:0 prq:0 1mp:0 1mb:0 1mt:0)
-EntranceNode: coke(t:2 pq:0 bq:0 tq:0 rt:0 prq:0 1mp:0 1mb:0 1mt:0)
--coke(t:2 pq:0 bq:0 tq:0 rt:0 prq:0 1mp:0 1mb:0 1mt:0)
---/coke/coke(t:0 pq:0 bq:0 tq:0 rt:0 prq:0 1mp:0 1mb:0 1mt:0)
-EntranceNode: sentinel_default_context(t:0 pq:0 bq:0 tq:0 rt:0 prq:0 1mp:0 1mb:0 1mt:0)
-EntranceNode: book(t:1 pq:0 bq:0 tq:0 rt:0 prq:0 1mp:0 1mb:0 1mt:0)
--book(t:1 pq:0 bq:0 tq:0 rt:0 prq:0 1mp:0 1mb:0 1mt:0)
---/book/coke(t:0 pq:0 bq:0 tq:0 rt:0 prq:0 1mp:0 1mb:0 1mt:0)


```

`book` and `coke` are serviceId. 

`---/book/coke` is api path.



## Integration with Sentinel DashBord

1. start [Sentinel DashBord](https://github.com/alibaba/Sentinel/wiki/%E6%8E%A7%E5%88%B6%E5%8F%B0).

2. add vm property to zuul-gateway. `-Dcsp.sentinel.dashboard.server=localhost:8088 -Dcsp.sentinel.api.port=18990`

## Fallback

zuul provide `FallbackProvider` to cope with fall back logic. 
