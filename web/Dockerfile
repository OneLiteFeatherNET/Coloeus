# Staff web UI: the Nuxt 4 pages and their Nitro server (the backend-for-frontend), one image on Node 24.
# The build stage installs from the lockfile and builds; the runtime stage carries only `.output` and runs as a
# non-root user on port 3000. Settings come from the environment (see server/lib/config.ts).

FROM node:24-bookworm-slim AS build
WORKDIR /app
RUN corepack enable
COPY . .
RUN pnpm install --frozen-lockfile
RUN pnpm build

FROM node:24-bookworm-slim AS runtime
ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=3000
WORKDIR /app
RUN groupadd --system --gid 10001 coloeus \
 && useradd --system --uid 10001 --gid coloeus --home-dir /app --shell /usr/sbin/nologin coloeus
COPY --from=build /app/.output ./.output
USER 10001:10001
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD ["node", "-e", "fetch('http://127.0.0.1:3000/admin/healthz').then(r => process.exit(r.ok ? 0 : 1), () => process.exit(1))"]
CMD ["node", ".output/server/index.mjs"]
