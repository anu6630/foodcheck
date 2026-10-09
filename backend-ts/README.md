# FoodCheck Backend (TypeScript / Neon Functions)

Clean-architecture rewrite of the Python FastAPI service, deployed as a Neon Function against Neon Postgres. Product-request photos go to Cloudflare R2 (`foodcheck-media`).

## Layout

- `src/domain` — entities, ports, use cases
- `src/adapters` — HTTP routes (Hono), Postgres repos, Gemini / OFF / R2
- `src/infrastructure` — DB pool, schema ensure
- `functions/api.ts` — composition root (default export for Neon)
- `neon.ts` — function slug `foodcheck` + env whitelist
- `data/fssai-unmatched-ins.json` — Appendix A INS that did not map to a catalog ingredient. Deferred; `how_to_fix` is the full later patch (no new PDF needed).

## Local

```bash
cp .env.local  # already gitignored; copy from lottery_app patterns
npm install
npm run seed
npm run dev    # http://0.0.0.0:8999
```

## Deploy

```bash
npm run deploy:foodcheck
```

Production URL:

`https://br-hidden-waterfall-b5gh3ebj-foodcheck.compute.c-7.us-east-2.aws.neon.tech`

## Env (names)

`DATABASE_URL`, `GEMINI_API_KEY` / `GOOGLE_API_KEY`, `ADMIN_TOKEN`, `MOCK_GEMINI`, `MOCK_OFF`, `MOCK_SCAN_TYPE`, `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_FOODCHECK_BUCKET`
