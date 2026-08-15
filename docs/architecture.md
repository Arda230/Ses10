# Architecture

The project uses a dependency direction that keeps business logic independent from delivery and infrastructure concerns:

```text
http (delivery) -> application (use cases) -> domain (business rules)
                                  ^
                    infrastructure (adapters) implements application ports
```

- `domain/`: entities, value objects, domain events, and business invariants. It must not import framework, HTTP, database, or environment code.
- `application/`: use cases and ports. It coordinates domain objects and declares interfaces for external dependencies.
- `infrastructure/`: adapters for persistence, queues, third-party clients, and observability. It depends on application ports.
- `http/`: transport routes, request parsing, response mapping, and middleware.
- `config/`: validated runtime configuration.
- `shared/`: small cross-cutting primitives with no business meaning.

Composition occurs only in `src/main.ts`. Add each feature as a vertical module spanning these layers rather than placing business logic in handlers.
