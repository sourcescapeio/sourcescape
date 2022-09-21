FROM nginx:1.13.12-alpine

ADD docker/local/nginx.conf /etc/nginx/nginx.conf
