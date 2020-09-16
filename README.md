# scalecube-gateway

ScaLecube API Gateway allows service consumers interact with scalecube microservices cluster.

![image](https://user-images.githubusercontent.com/1706296/58700290-4c569d80-83a8-11e9-8322-7e9d757dfad2.png)

Read about it here: 
 - [Api Gateway Pattern by Chris Richardson](https://microservices.io/patterns/apigateway.html)
 - [Api Gateway Pattern by Ronen Nachmias](https://www.linkedin.com/pulse/api-gateway-pattern-ronen-hamias/)

## API-Gateway:

Available api-gateways are [rsocket](/services-gateway-rsocket), [http](/services-gateway-http) and [websocket](/services-gateway-websocket)

Basic API-Gateway example:

```java

    Microservices.builder()
        .discovery(options -> options.seeds(seed.discovery().address()))
        .services(...) // OPTIONAL: services (if any) as part of this node.

        // configure list of gateways plugins exposing the apis
        .gateway(options -> new WebsocketGateway(options.id("ws").port(8080)))
        .gateway(options -> new HttpGateway(options.id("http").port(7070)))
        .gateway(options -> new RSocketGateway(options.id("rsws").port(9090)))
        
        .startAwait();
        
        // HINT: you can try connect using the api sandbox to these ports to try the api.
        // http://scalecube.io/api-sandbox/app/index.html
```

**Service API-Gateway providers:**

releases: https://github.com/scalecube/scalecube-services/releases

* HTTP-Gateway - [scalecube-services-gateway-http](/services-gateway-http)
* RSocket-Gateway - [scalecube-services-gateway-rsocket](/services-gateway-rsocket)
* WebSocket - [scalecube-services-gateway-websocket](services-gateway-websocket)


 <!-- 
   scalecube message serialization providers:
   -->

 <!-- jackson scalecube messages codec -->
 <dependency>
  <groupId>io.scalecube</groupId>
  <artifactId>scalecube-services-transport-jackson</artifactId>
  <version>${scalecube.version}</version>
 </dependency>

<!-- protostuff scalecube messages codec -->
 <dependency>
  <groupId>io.scalecube</groupId>
  <artifactId>scalecube-services-transport-protostuff</artifactId>
  <version>${scalecube.version}</version>
 </dependency>

 <!--
    scalecube service discovery provider   
   -->
 <dependency>
  <groupId>io.scalecube</groupId>
  <artifactId>scalecube-services-discovery</artifactId>
  <version>${scalecube.version}</version>
 </dependency>
