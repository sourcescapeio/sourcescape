# Stage 1
FROM amazoncorretto:8u342-alpine3.15-jdk AS builder

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
RUN sbt "project rambutanInitializer" clean stage
RUN sbt "project rambutanLocal" clean stage
RUN sbt "project rambutanIndexer" clean stage

# Stage 2
FROM amazoncorretto:8u342-alpine3.15-jre
ADD ./docker/desktop/rambutan.conf /opt/conf/rambutan.conf
COPY --from=builder /usr/build/app/apps/rambutan-initializer/target/universal /opt/initializer
COPY --from=builder /usr/build/app/apps/rambutan-local/target/universal /opt/api
COPY --from=builder /usr/build/app/apps/rambutan-indexer/target/universal /opt/indexer

CMD tail -f /dev/null
