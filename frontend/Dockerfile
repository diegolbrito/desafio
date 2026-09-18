# Build: gera o bundle estatico de producao.
FROM node:22 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# String vazia (nao "unset") faz o httpClient usar caminho relativo (/api/...),
# que o nginx abaixo repassa para o servico backend - evita CORS e nao exige
# saber o host publico do backend em build time (ver SPEC.md, "Configuracao":
# URL da API via ambiente, nunca hardcoded).
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# Runtime: bundle estatico servido pelo nginx, com /api proxied para o backend.
FROM nginx:1.27-alpine AS runtime
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
