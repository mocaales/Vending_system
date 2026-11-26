import express, { Request, Response } from "express";
import path from "path";
import cors from "cors";
import * as OpenApiValidator from "express-openapi-validator";
import { Pool } from "pg";
import { traceIdMiddleware } from "./core/trace";
import { rejectSuspiciousPath } from "./core/rawPath";
import { errorHandler } from "./core/errors";
import { config } from "./core/config";
import { createHealthRouter } from "./core/health";
import { sanitizeHeaders } from "./core/jwt";
import { createAuthRouter } from "./auth/router";
import { createProxyRouter } from "./proxy/router";
import { createDeviceRouter } from "./device/router";
import { createOperatorsRouter } from "./operators/router";
import { createPublicRouter } from "./public/router";
import { createOperatorRouter } from "./operator/router";

export const createApp = (db: Pool): express.Application => {
  const app = express();

  app.use(traceIdMiddleware);
  app.use(rejectSuspiciousPath);
  app.use(
    cors({
      origin: config.corsOrigins,
      credentials: false,
      methods: ["GET", "POST", "PUT", "DELETE"],
      allowedHeaders: ["Authorization", "Content-Type", "X-Trace-Id", "X-Machine-Key"],
      exposedHeaders: ["X-Trace-Id", "Retry-After", "Location"],
      maxAge: 600,
    }),
  );
  app.use(express.json({ limit: "100kb" }));
  app.use(sanitizeHeaders);

  app.use(createHealthRouter(db));

  const specPath = path.join(__dirname, "..", "openapi.yaml");
  app.use(
    OpenApiValidator.middleware({
      apiSpec: specPath,
      validateRequests: { allowUnknownQueryParameters: false },
      validateResponses: false,
      validateSecurity: false,
      ignoreUndocumented: true,
      ignorePaths:
        /^\/api\/v1\/(machines|products|categories|inventory|measurements|monitoring|alerts|thresholds|sales)(\/|$)/,
    }),
  );

  app.use(createAuthRouter(db));
  app.use(createPublicRouter());
  app.use(createOperatorRouter(db));
  app.use(createOperatorsRouter(db));
  app.use(createDeviceRouter());
  app.use(createProxyRouter(db));

  app.use((_req: Request, res: Response) => {
    const traceId = res.getHeader("X-Trace-Id") as string;
    res.status(404).json({ code: "NOT_FOUND", message: "Route not found", traceId });
  });

  app.use(errorHandler);

  return app;
};
