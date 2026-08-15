export type LogLevel = "debug" | "info" | "warn" | "error";

export interface Logger {
  debug(message: string, context?: Record<string, unknown>): void;
  info(message: string, context?: Record<string, unknown>): void;
  warn(message: string, context?: Record<string, unknown>): void;
  error(message: string, context?: Record<string, unknown>): void;
}

const severity: Record<LogLevel, number> = {
  debug: 10,
  info: 20,
  warn: 30,
  error: 40,
};

export class ConsoleLogger implements Logger {
  public constructor(private readonly minimumLevel: LogLevel) {}

  public debug(message: string, context?: Record<string, unknown>): void {
    this.write("debug", message, context);
  }

  public info(message: string, context?: Record<string, unknown>): void {
    this.write("info", message, context);
  }

  public warn(message: string, context?: Record<string, unknown>): void {
    this.write("warn", message, context);
  }

  public error(message: string, context?: Record<string, unknown>): void {
    this.write("error", message, context);
  }

  private write(
    level: LogLevel,
    message: string,
    context?: Record<string, unknown>,
  ): void {
    if (severity[level] < severity[this.minimumLevel]) return;

    const entry = JSON.stringify({
      timestamp: new Date().toISOString(),
      level,
      message,
      ...context,
    });
    (level === "error" ? console.error : console.log)(entry);
  }
}
