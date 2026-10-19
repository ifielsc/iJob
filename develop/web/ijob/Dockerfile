# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

ARG NODE_IMAGE
ARG NGINX_IMAGE
FROM ${NODE_IMAGE} AS build
WORKDIR /build
RUN npm install --global pnpm@11.19.0
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY patches ./patches
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM ${NGINX_IMAGE} AS runtime
USER root
RUN apk add --no-cache zlib=1.3.2-r1 libx11=1.8.14-r0 libxpm=3.5.20-r0
COPY --from=build /build/dist /usr/share/nginx/html
COPY tools/docker/default.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
USER 101:101
HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
ENTRYPOINT ["nginx", "-g", "daemon off;"]
