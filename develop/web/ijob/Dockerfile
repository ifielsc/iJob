# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

ARG NODE_IMAGE
ARG NGINX_IMAGE
FROM ${NODE_IMAGE} AS build-base
ARG PNPM_ARCHIVE_URL=https://registry.npmjs.org/pnpm/-/pnpm-11.28.8.tgz
ADD --checksum=sha256:02bf6cec47fe28f30a224204f924175c2d8750c60e84e315a25cba57ce84da6a ${PNPM_ARCHIVE_URL} /tmp/ijob-pnpm.tgz
RUN apk upgrade --no-cache \
    && mkdir -p /usr/local/lib/node_modules/pnpm \
    && tar -xzf /tmp/ijob-pnpm.tgz -C /usr/local/lib/node_modules/pnpm --strip-components=1 \
    && chmod 755 /usr/local/lib/node_modules/pnpm/bin/pnpm.cjs \
    && ln -s /usr/local/lib/node_modules/pnpm/bin/pnpm.cjs /usr/local/bin/pnpm \
    && rm /tmp/ijob-pnpm.tgz \
    && rm -rf /usr/local/lib/node_modules/npm /opt/yarn-* \
    && rm -f /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/yarn /usr/local/bin/yarnpkg \
    && pnpm --version

FROM build-base AS build
WORKDIR /build
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY patches ./patches
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM ${NGINX_IMAGE} AS runtime-base
USER root
RUN apk upgrade --no-cache

FROM runtime-base AS runtime
COPY --from=build /build/dist /usr/share/nginx/html
COPY tools/docker/default.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
USER 101:101
HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
ENTRYPOINT ["nginx", "-g", "daemon off;"]
