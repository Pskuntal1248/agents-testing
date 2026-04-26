# Quick Test Guide

## Start Everything
```bash
cd byoc-app && docker build -t byoc-app:test . && cd ..
docker network create opensre-net
docker run -d --name db --network opensre-net --network-alias postgres \
  -e POSTGRES_DB=testdb -e POSTGRES_USER=testuser -e POSTGRES_PASSWORD=testpass \
  -v $(pwd)/src/main/resources/mock-schema-seed.sql:/docker-entrypoint-initdb.d/init.sql \
  -p 5432:5432 postgres:15-alpine && sleep 10
docker run -d --name app --network srelab-net \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/testdb \
  -e SPRING_DATASOURCE_USERNAME=testuser -e SPRING_DATASOURCE_PASSWORD=testpass \
  -p 8080:8080 byoc-app:test && sleep 15
```

## Test
```bash
curl http://localhost:8080/health
curl http://localhost:8080/api/users
curl http://localhost:8080/api/facilities
curl -X POST http://localhost:8080/api/users -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@test.com"}'
```

## Stop
```bash
docker stop app db && docker rm app db && docker network rm opensre-net
```
