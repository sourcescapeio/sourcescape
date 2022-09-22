FROM node:14.14-alpine3.11

ADD microservices/primadonna /usr/src/app

WORKDIR /usr/src/app

RUN npm install

CMD npm start
