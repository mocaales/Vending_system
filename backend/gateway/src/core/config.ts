const required = (name: string): string => {
  const val = process.env[name];
  if (!val) throw new Error(`Missing required env var: ${name}`);
  return val;
};

const optional = (name: string, fallback: string): string => process.env[name] ?? fallback;

export const config = {
  port: parseInt(optional("PORT", "8000"), 10),
  nodeEnv: optional("NODE_ENV", "production"),
  databaseUrl: required("DATABASE_URL"),
  gatewayServiceToken: required("GATEWAY_SERVICE_TOKEN"),
  jwtSecret: required("JWT_SECRET"),
  jwtExpiresInSeconds: parseInt(optional("JWT_TTL_MINUTES", "480"), 10) * 60,
  bootstrapEmail: optional("BOOTSTRAP_OPERATOR_EMAIL", ""),
  bootstrapPassword: optional("BOOTSTRAP_OPERATOR_PASSWORD", ""),
};
