# concurrency-solution

동시성 이슈 해결방법(Synchronize, DB Lock, Redis Distributed Lock)

### 목차

- [작업환경 구성](#작업환경-구성)
    - [1. DB 세팅](#1-db-세팅)
    - [2. 재고 감소 로직 작성](#2-재고-감소-로직-작성)
    - [3. 동시에 요청이 들어올 경우, 발생하는 동시성 이슈 구현](#3-동시에-요청이-들어올-경우-발생하는-동시성-이슈-구현)
- [동시성 이슈 해결 방법 비교](#동시성-이슈-해결-방법-비교)
    - [1. Synchronized](#1-synchronized)
        - [Synchronized 내용 정리](#synchronized-내용-정리)
        - [Synchronized 문제점](#synchronized-문제점)

<br/>

## 작업환경 구성

### 1. DB 세팅

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

### 2. 재고 감소 로직 작성

```java
public void decrease(Long quantity) {
    if (this.quantity < quantity) {
        throw new RuntimeException("재고는 0개 미만이 될 수 없습니다.");
    }

    this.quantity -= quantity;
} 
```

<br/>

### 3. 동시에 요청이 들어올 경우, 발생하는 동시성 이슈 구현

```java

@Test
@DisplayName("재고 감소 요청이 동시에 n건이 들어왔을 경우, 재고가 n만큼 감소해야 한다.")
void 재고감소_동시요청() throws Exception {

    // given
    final Long id = 1L;
    final Long quantity = 1L;
    final int threadCount = 100;
    final ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    final CountDownLatch countDownLatch = new CountDownLatch(threadCount);

    // when
    for (int i = 0; i < threadCount; i++) {
        executorService.submit(() -> {
            try {
                stockService.decrease(id, quantity);
            } finally {
                countDownLatch.countDown();
            }
        });
    }
    countDownLatch.await();

    // then
    final Stock stock = stockRepository.findById(id).orElseThrow();
    Assertions.assertThat(stock.getQuantity()).isEqualTo(0);
}
```

## 동시성 이슈 해결 방법 비교

### 1. Synchronized

```java
// @Transactional
public synchronized void decrease(Long id, Long quantity) {
    // Stock 조회
    final Stock stock = stockRepository.findById(id).orElseThrow();

    // 재고 감소
    stock.decrease(quantity);

    // 갱신된 값을 저장
    stockRepository.save(stock);
}
```

#### Synchronized 내용 정리

1. `StockService.decrease()` 메서드 레벨에 `synchronized` 를 작성
    - **이때, `@Transactional`을 사용할 경우 여전히 동시성 이슈가 발생.**
        - 이는 기본 적으로 트랜잭션의 격리 수준이 `READ_COMMITTED`로 동작하기 때문.
        - 조회 후 메모리 상에서 값을 변경하는 동안, 다른 트랜잭션에서 데이터를 수정할 수 있음.
        - 결국 값이 덮어쓰기되는 손실된 갱신(Lost Update) 발생.
2. 따라서 `@Transactional`을 주석처리한 후, `synchronized`를 사용하면 동시성 이슈를 해결 가능.

<br/>

#### Synchronized 문제점

- `Synchronized`는 **하나의 프로세스 안에서만 동시성이 보장**.
- 따라서 여러 서버에서 DB에 접근할 경우, 여전히 동시에 데이터에 접근이 가능.
    - → 동시성 이슈가 여전히 발생