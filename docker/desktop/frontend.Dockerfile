# Stage 1
FROM node:16.16-alpine3.15 as builder

ADD frontend /usr/src/app

WORKDIR /usr/src/app

RUN npm install
RUN REACT_APP_BUILD_TARGET=local npm run build

# Stage 2
FROM node:16.16-alpine3.15
RUN npm install -g serve
COPY --from=builder /usr/src/app/build /usr/src/app/build

WORKDIR /usr/src/app
CMD serve -s build -l 3000
