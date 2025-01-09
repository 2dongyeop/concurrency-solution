# concurrency-solution
동시성 이슈 해결방법(Synchronize, DB Lock, Redis Distributed Lock)

<br/>

## 작업환경 DB 세팅
```shell
# Docker Run
$ docker run -d -p 3306:3306 \
    -e MYSQL_ROOT_PASSWORD=1234 --name mysql mysql
    
# 실행 결과 확인
$ docker ps

# DB 테이블 생성
$ docker exec -it mysql bash     # Docker Container Bash 접속
$ mysql -u root -p1234           # mysql 접속
$ CREATE DATABASE stock_example; # DB Table 생성
$ USE stock_example;         
```

<br/>

