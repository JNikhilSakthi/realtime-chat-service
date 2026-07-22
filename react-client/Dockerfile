# ---- Build stage -------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /build

COPY package.json ./
RUN npm install

COPY . .
RUN npm run build

# ---- Runtime stage: static files served by nginx, which also reverse-proxies
#      /api and /ws (with WebSocket Upgrade support) to the backend service. ----
FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -qO- http://localhost:80/ || exit 1
