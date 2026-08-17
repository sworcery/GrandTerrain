FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:20-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# Optional build-time override. When unset, the browser calls the worker
# through the same-origin /worker/* proxy (app/worker/[...path]/route.ts),
# which forwards to the runtime WORKER_URL env var below. Only set this if you
# want the browser to call the worker directly (cross-origin; needs CORS).
ARG NEXT_PUBLIC_WORKER_URL
ENV NEXT_PUBLIC_WORKER_URL=$NEXT_PUBLIC_WORKER_URL
RUN npm run build

FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production HOSTNAME=0.0.0.0 PORT=3000
# Runtime: where the Next server reaches the worker (container-internal DNS is
# fine, e.g. http://worker:8080). Read per request — no rebuild needed to change.
ENV WORKER_URL=http://localhost:8080
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public
EXPOSE 3000
CMD ["node", "server.js"]
