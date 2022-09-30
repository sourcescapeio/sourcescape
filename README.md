
[**Read the Documentation here**](https://docs.sourcescape.io)



### Dev commands

```bash
sbt -mem 2048 "project rambutanLocal" run
sbt -mem 2048 "project rambutanIndexer" run

sbt "project rambutanInitializer" run
```

### Dev ports

API => 9003

Indexer => 9002

Primadonna => 3001
    ESPrima parser


### Building local images

```bash
docker compose -f docker-compose.desktop.yml build

docker compose -f docker-compose.desktop.yml build backend
```
