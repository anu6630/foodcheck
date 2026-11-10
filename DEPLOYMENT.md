# Deployment

**Git `main` is the source of truth.** This site (foodcheck.lifex.biz) is served from a self-hosted Nomad cluster;
its Vercel project is only the fallback. A push/merge to `main` updates both:

```
git push main ─┬─► Vercel Git integration ─────────────► fallback deployment
               └─► .github/workflows/cluster-image.yml (anu6630/web-deploy action)
                     → ghcr.io/anu6630/site-foodcheck:main
                     → cluster job web-deployer rolls Nomad job web-foodcheck to it (~2 min,
                       rolling, health check on `/`, automatic revert if unhealthy)

visitor → Cloudflare → Worker lifex-failover ─┬─ primary: Cloudflare Tunnel → the cluster (1 copy; moved to
                                              │            another node within ~1 min if its node is lost)
                                              └─ fallback: foodcheck-lifex-biz-anu6630s-projects.vercel.app (cluster unreachable)
```

The response header `x-served-by: nomad | vercel` shows which one answered.

## Rules for changes (people and AI agents)

- Deploy only by merging to `main`. Never `vercel --prod` / `vercel deploy` from a laptop, and never build
  images by hand: a local deploy ships code that is not in git and the two deployments drift apart.
- `web` is built like Vercel does (package manager and version from the lockfile, full install so `postinstall` hooks run, then the project's own `build` script) and served with `next start` (Node 24) from the complete build output. Each copy keeps its own Next.js cache (ISR/data cache are per copy).
- Must work under plain `next start` on Node 24 — no Vercel-only runtime features (Edge Config,
  Vercel KV/Blob bindings, `VERCEL_*` env vars without a fallback). Files read at request time must be committed.
- `NEXT_PUBLIC_*` values are baked in at build time: changing one needs a new build (push to main).
- Build-time env comes from the repo secret `WEB_BUILD_ENV` (the Vercel production values). When you add or
  change an env var, update the Vercel project **and** that secret. Server-side runtime env on the cluster:
  none (Nomad Variable `nomad/jobs/web-foodcheck`).
- A build that fails its rollout is reverted automatically and not retried until `main` changes.

The build kit, deployer and failover Worker live in the private repo `anu6630/web-deploy` (README there).
