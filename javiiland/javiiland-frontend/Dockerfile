# ==========================================
# Etapa 1: Compilación de la aplicación Vite
# ==========================================
FROM node:22-alpine AS build

# Install the latest security updates for packages in the base image
RUN apk upgrade --no-cache

WORKDIR /app

# Copiar manifiestos e instalar dependencias
COPY package*.json ./
RUN npm install

# Copiar el código fuente y generar los archivos estáticos en /app/dist
COPY . .
RUN npm run build

# ==========================================
# Etapa 2: Servidor web de producción NGINX
# ==========================================
FROM nginx:stable-alpine

# Install the latest security updates for packages in the base image
RUN apk upgrade --no-cache

# Limpiar archivos por defecto de NGINX
RUN rm -rf /usr/share/nginx/html/*

# Copiar el resultado de la compilación de Vite a la carpeta pública de NGINX
COPY --from=build /app/dist /usr/share/nginx/html

# Configurar NGINX para manejar el enrutamiento SPA (React Router / subrutas)
RUN printf 'server {\n\
    listen 80;\n\
    location / {\n\
        root /usr/share/nginx/html;\n\
        index index.html index.htm;\n\
        try_files $uri $uri/ /index.html;\n\
    }\n\
}\n' > /etc/nginx/conf.d/default.conf

# NGINX escucha internamente en el puerto 80
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]