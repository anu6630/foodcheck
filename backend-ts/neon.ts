import { defineConfig } from "@neon/config/v1";

/**
 * Neon Functions config for FoodCheck.
 * Slug `foodcheck` so we never overwrite lottery/crossword functions
 * on the shared Lifex Neon project.
 *
 * Deploy: npm run deploy:foodcheck
 */
export default defineConfig({
  functions: {
    foodcheck: {
      name: "FoodCheck API",
      source: "functions/api.ts",
      env: {
        DATABASE_URL: process.env.DATABASE_URL!,
        GEMINI_API_KEY: process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY || "",
        GOOGLE_API_KEY: process.env.GOOGLE_API_KEY || process.env.GEMINI_API_KEY || "",
        ADMIN_TOKEN: process.env.ADMIN_TOKEN || "123",
        MOCK_GEMINI: process.env.MOCK_GEMINI || "false",
        MOCK_OFF: process.env.MOCK_OFF || "false",
        MOCK_SCAN_TYPE: process.env.MOCK_SCAN_TYPE || "",
        R2_ACCOUNT_ID: process.env.R2_ACCOUNT_ID!,
        R2_ACCESS_KEY_ID: process.env.R2_ACCESS_KEY_ID!,
        R2_SECRET_ACCESS_KEY: process.env.R2_SECRET_ACCESS_KEY!,
        R2_FOODCHECK_BUCKET: process.env.R2_FOODCHECK_BUCKET!,
      },
    },
  },
});
