# Stage 1
FROM openjdk:8u232-jdk-stretch AS builder

ADD ./backend /usr/build/app

ARG SBT_VERSION=1.7.1

WORKDIR /usr/build/app

RUN \
  curl -L -o sbt-$SBT_VERSION.deb https://repo.scala-sbt.org/scalasbt/debian/sbt-$SBT_VERSION.deb && \
  dpkg -i sbt-$SBT_VERSION.deb && \
  rm sbt-$SBT_VERSION.deb && \
  apt-get update && \
  apt-get install sbt && \
  sbt sbtVersion

ARG SBT_OPTS="-Xmx4096m -Xms1024m -Xss32m -XX:ReservedCodeCacheSize=128m -XX:+UseCodeCacheFlushing -XX:+UseCompressedOops -XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled"
RUN sbt "project rambutanLocal" clean stage

# Stage 2
FROM openjdk:8u232-jre-stretch
ADD ./docker/desktop/rambutan.conf /opt/conf/rambutan.conf
COPY --from=builder /usr/build/app/apps/rambutan-local/target/universal /opt/api

CMD tail -f /dev/null
