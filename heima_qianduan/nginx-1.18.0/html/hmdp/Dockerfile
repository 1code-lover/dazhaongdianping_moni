# ============================================================================
# 前端一体化镜像：Node 构建 → nginx 托管（含 API 反代配置）
# ============================================================================
FROM node:20 AS build
WORKDIR /app
# npm 国内镜像加速
RUN npm config set registry https://registry.npmmirror.com
COPY package.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:1.24-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx-docker.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
