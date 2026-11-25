import express, { Request, Response } from "express";
import path from "path";
import * as OpenApiValidator from "express-openapi-validator";
import { Pool } from "pg";
import { traceIdMiddleware } from "./core/trace";
import { errorHandler } from "./core/errors";
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

  app.use(express.json());
  app.use(traceIdMiddleware);
  app.use(sanitizeHeaders);

  // Health routes (not in OpenAPI)
  app.use(createHealthRouter(db));

  // Auth routes
  app.use(createAuthRouter(db));

  // Public routes (no auth, rate limited)
  app.use(createPublicRouter());

  // Operator composed views (dashboard, machine overview)
  app.use(createOperatorRouter(db));

  // Operator management routes
  app.use(createOperatorsRouter(db));

  // Device routes (machine key auth, no JWT)
  app.use(createDeviceRouter());

  // Proxy routes (operator-protected, forwarded to backend services)
  app.use(createProxyRouter(db));

  // OpenAPI validation (only when spec exists and has paths)
  const specPath = path.join(__dirname, "..", "openapi.yaml");
  app.use(
    OpenApiValidator.middleware({
      apiSpec: specPath,
      validateRequests: true,
      validateResponses: false,
    }),
  );

  // 404 for unknown routes
  app.use((_req: Request, res: Response) => {
    const traceId = res.getHeader("X-Trace-Id") as string;
    res.status(404).json({ code: "NOT_FOUND", message: "Route not found", traceId });
  });

  app.use(errorHandler);

  return app;
};
