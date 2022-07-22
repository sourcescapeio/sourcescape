FROM nginx:1.13.12-alpine

ADD docker/sourcescape/nginx.conf /etc/nginx/nginx.conf
