# a2a-spring

Spring Boot integration layer for the [A2A Java SDK](https://github.com/a2aproject/a2a-java)
(`org.a2aproject.sdk:1.2.0.Final`).

This is a migration of `a2a-jakarta` from Jakarta EE / WildFly to Spring Boot 3.5. It does **not**
implement the A2A protocol — the SDK does that. This project supplies the web plumbing: version
routing filters, transport controllers, and the Spring bean wiring that replaces CDI. See
[MIGRATION.md](MIGRATION.md) for the full mapping.

## Requirements

- JDK 17+
- Maven 3.9+

## Build

```bash
mvn clean install
```

13 modules, 64 tests.

## Modules

| Module | Artifact | Purpose |
|---|---|---|
| `http-common` | `a2a-spring-http-common` | Version routing filters, SSE subscriber, CDI→Spring bean wiring |
| `impl/jsonrpc` | `a2a-spring-jsonrpc` | JSON-RPC transport (protocol v1.0) |
| `impl/rest` | `a2a-spring-rest` | HTTP+JSON/REST transport (v1.0) |
| `impl/grpc` | `a2a-spring-grpc` | gRPC transport (v1.0) |
| `compat-0.3/{jsonrpc,rest,grpc}` | `a2a-spring-compat-0.3-*` | Protocol v0.3 compatibility |
| `examples/simple/{server,client}` | `a2a-spring-example-simple-*` | Runnable example |
| `tests` | `a2a-spring-tests` | Integration tests |

## Running the example server

The server module has one profile per transport. `jsonrpc` is active by default:

```bash
mvn -pl examples/simple/server -am install                  # JSON-RPC (default)
mvn -pl examples/simple/server -am install -P rest          # REST + JSON-RPC
mvn -pl examples/simple/server -am install -P grpc          # gRPC + JSON-RPC

java -jar examples/simple/server/target/a2a-spring-example-simple-server-1.0.0.Final-SNAPSHOT.jar
```

Listens on `http://localhost:8080`.

```bash
# Agent card
curl http://localhost:8080/.well-known/agent-card.json

# Send a message
curl -X POST http://localhost:8080/ \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","id":1,"method":"SendMessage","params":{"message":
       {"messageId":"m-1","role":"ROLE_USER","parts":[{"text":"World"}]}}}'
```

The second call returns a completed task whose artifact contains `Hello World`.

> The `A2A-Version` header is required when more than one protocol version is on the classpath, and
> the SDK's own validator defaults to `0.3` when it is absent. Send `A2A-Version: 1.0` for v1.0 agents.

## Running the example client

```bash
mvn -pl examples/simple/client exec:java -P run-jsonrpc     # or run-rest / run-grpc
```

## Using it in your own application

Add the transport you want:

```xml
<dependency>
  <groupId>org.a2a.spring</groupId>
  <artifactId>a2a-spring-jsonrpc</artifactId>
  <version>1.0.0.Final-SNAPSHOT</version>
</dependency>
```

Then supply two beans — an agent card named `publicAgentCard`, and an `AgentExecutor`. Everything
else (task store, queue manager, event bus, request handler, transport handlers) is auto-configured
and every bean is `@ConditionalOnMissingBean`, so you can override any of it.

```java
@SpringBootApplication
@ComponentScan(basePackages = {"com.example", "org.a2a.spring"}, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
})
public class MyAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyAgentApplication.class, args);
    }

    @Bean("publicAgentCard")
    public AgentCard agentCard() { /* ... */ }

    @Bean
    public AgentExecutor agentExecutor() { /* ... */ }
}
```

> **If you override `@ComponentScan`, keep those two `CUSTOM` filters.** `@SpringBootApplication`
> declares them by default, and declaring your own `@ComponentScan` *replaces* them. Without
> `AutoConfigurationExcludeFilter` the auto-configuration classes get component-scanned as ordinary
> `@Configuration` classes and are evaluated before your own `@Bean` methods are registered — every
> `@ConditionalOnMissingBean` then fails and the transport handlers are never created.

Scanning `org.a2a.spring` is only needed because the transport `@RestController`s live in that
package. The auto-configurations are picked up from `AutoConfiguration.imports` regardless.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `a2a.executor.core-pool-size` | 8 | Async executor core threads |
| `a2a.executor.max-pool-size` | 64 | Async executor max threads |
| `a2a.executor.queue-capacity` | 1024 | Async executor queue depth |
| `a2a.executor.thread-name-prefix` | `a2a-async-` | Thread name prefix |
| `a2a.executor.await-termination-seconds` | 30 | Shutdown grace period |

## Protocol versions

With both v1.0 and v0.3 modules on the classpath, requests are routed by the `A2A-Version` header;
v0.3 is the default when the header is absent. Internally each version is served from its own path
prefix (`/a2a_jsonrpc_v1.0`, `/a2a_rest_v0.3`, …) and the routing filters rewrite public URLs onto
those prefixes. Clients never see the internal paths.

## License

Apache-2.0.
