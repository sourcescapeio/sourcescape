FROM nginx:1.13.12-alpine

ADD docker/desktop/nginx.conf /etc/nginx/nginx.conf
