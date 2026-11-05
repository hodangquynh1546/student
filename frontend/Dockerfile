FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/templates/default.conf.template
RUN sed -i 's|http://backend:8080|${BACKEND_UPSTREAM}|g; s|listen 80;|listen ${PORT};|' /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV BACKEND_UPSTREAM=http://backend:8080 PORT=80
EXPOSE 80
