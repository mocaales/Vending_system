// Set required env vars before any module is imported in tests.
// Testcontainers replaces DATABASE_URL per test suite.
process.env.DATABASE_URL = "postgresql://test:test@localhost:5432/test";
process.env.GATEWAY_SERVICE_TOKEN = "test-gateway-token";
process.env.JWT_SECRET = "test-secret-at-least-32-characters-long";
