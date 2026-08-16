import { drizzle, type NodePgDatabase } from "drizzle-orm/node-postgres";
import { Pool } from "pg";

import * as schema from "./schema.js";

export interface DatabaseClient {
  db: NodePgDatabase<typeof schema>;
  close(): Promise<void>;
}

export function createDatabaseClient(connectionString: string): DatabaseClient {
  const pool = new Pool({ connectionString, max: 10, idleTimeoutMillis: 30_000 });
  return {
    db: drizzle(pool, { schema }),
    close: () => pool.end(),
  };
}
