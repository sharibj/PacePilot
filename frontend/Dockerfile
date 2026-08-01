# syntax=docker/dockerfile:1

# ---- base ----
FROM node:22-alpine AS base
WORKDIR /app
COPY package*.json ./
RUN npm ci

# ---- dev: vite dev server, source mounted by compose ----
FROM base AS dev
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host"]

# ---- build: produce static assets ----
FROM base AS build
COPY . .
RUN npm run build

# ---- prod: serve static assets via nginx ----
FROM nginx:1.27-alpine AS prod
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
