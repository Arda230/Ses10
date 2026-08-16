import type { Server } from "node:http";
import "dotenv/config";

import { loadConfig } from "./config/env.js";
import { createHttpServer } from "./http/server.js";
import { ConsoleLogger } from "./shared/logger.js";

const config = loadConfig();
const logger = new ConsoleLogger(config.logLevel);
const server = createHttpServer({ logger, livekit: config.livekit });

function stop(signal: string, httpServer: Server): void {
  logger.info("Shutdown requested", { signal });
  httpServer.close((error) => {
    if (error) {
      logger.error("Shutdown failed", { error: error.message });
      process.exitCode = 1;
    }
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
