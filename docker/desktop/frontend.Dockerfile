# Stage 1
FROM node:14.14-alpine3.11 as builder

ADD frontend /usr/src/app

WORKDIR /usr/src/app

RUN npm install
RUN REACT_APP_BUILD_TARGET=local npm run build

# Stage 2
FROM node:14.14-alpine3.11
RUN npm install -g serve
COPY --from=builder /usr/src/app/build /usr/src/app/build

WORKDIR /usr/src/app
CMD serve -s build -l 3000
