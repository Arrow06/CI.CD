# Етап збирання: React-застосунок збирається у статичні файли (dist)
FROM --platform=$BUILDPLATFORM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Фінальний образ: nginx роздає статичні файли та проксіює /api на бекенд
FROM nginx:1.29-alpine
LABEL org.opencontainers.image.source="https://github.com/Arrow06/CI.CD"

# Налаштування середовища (перевизначаються в Kubernetes):
#   API_BASE_URL - адреса API для браузера; порожньо = той самий хост (через Ingress)
#   API_UPSTREAM - адреса бекенду для проксі /api у nginx
ENV API_BASE_URL="" \
    API_UPSTREAM="http://localhost:8080" \
    NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1

COPY docker/default.conf.template /etc/nginx/templates/default.conf.template
COPY --chmod=755 docker/40-app-config.sh /docker-entrypoint.d/40-app-config.sh
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
