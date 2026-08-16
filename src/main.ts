import type { Server } from "node:http";
import "dotenv/config";

import { AuthService } from "./application/auth/authService.js";
import { loadConfig } from "./config/env.js";
import { createAuthApi, readCookie } from "./http/authApi.js";
import { createHttpServer } from "./http/server.js";
import { createDatabaseClient } from "./infrastructure/database/client.js";
import { PostgresAuthRepository } from "./infrastructure/database/postgresAuthRepository.js";
import { ConsoleLogger } from "./shared/logger.js";

const config = loadConfig();
const logger = new ConsoleLogger(config.logLevel);
const database = createDatabaseClient(config.databaseUrl);
const authService = new AuthService(new PostgresAuthRepository(database.db), config.auth.sessionTtlSeconds);
const authApi = createAuthApi(authService, config.auth);
const authenticate = (request: import("node:http").IncomingMessage) => authService.me(readCookie(request, config.auth.cookieName));
const server = createHttpServer({ logger, livekit: config.livekit, authApi, authenticate });

function stop(signal: string, httpServer: Server): void {
  logger.info("Shutdown requested", { signal });
  httpServer.close((error) => {
    if (error) {
      logger.error("Shutdown failed", { error: error.message });
      process.exitCode = 1;
    }
    void database.close();
  });
}

server.listen(config.port, config.host, () => {
  logger.info("HTTP server listening", {
    environment: config.environment,
    host: config.host,
    port: config.port,
  });
});

process.once("SIGINT", () => stop("SIGINT", server));
process.once("SIGTERM", () => stop("SIGTERM", server));
