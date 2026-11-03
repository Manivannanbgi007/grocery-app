# ---------------------------------------------------------------------
# fresh-grocery container image (multi-stage build)
#   1. "build" stage: Node builds the shop into static files (npm run build -> dist/)
#   2. final stage:   NGINX serves those files (nginx/default.conf)
#
# Build:  docker build -t grocery/fresh-grocery:0.0.1 .
# The shop calls the API through the relative path /api, so something in front must send /api, /media and
# /delivery-media to the API gateway: in Kubernetes that is the Gateway (deployment/kubernetes/gateway/routes.yaml).
# No .env file is needed: without VITE_API_MOCKING=true the build talks to the real API.
# ---------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

# Install the packages first: this layer is cached until package-lock.json changes
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY . .
RUN npm run build


FROM nginx:1.28-alpine

COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
