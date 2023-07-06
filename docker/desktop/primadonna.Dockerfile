FROM node:16.18-alpine3.15

ADD microservices/primadonna /usr/src/app

WORKDIR /usr/src/app

RUN npm install

CMD npm start
