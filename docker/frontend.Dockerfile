FROM node:20-alpine AS build

WORKDIR /build
RUN corepack enable && corepack prepare pnpm@10.33.2 --activate
COPY frontend-Vue/package.json frontend-Vue/pnpm-lock.yaml ./frontend-Vue/
RUN cd frontend-Vue && pnpm install --frozen-lockfile
COPY frontend-Vue ./frontend-Vue
RUN cd frontend-Vue && pnpm run build:prod

FROM nginx:1.27-alpine
COPY --from=build /build/frontend-Vue/dist /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
