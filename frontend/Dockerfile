# syntax=docker/dockerfile:1

FROM node:24-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . ./
RUN npm run build

# Holds only the built site. cfpo copies /site out of this image (it needs sh and cp) and uploads it to Cloudflare Pages.
FROM busybox:1.38
COPY --from=build /build/dist/frontend/browser/ /site/
