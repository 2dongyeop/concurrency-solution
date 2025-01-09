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
    - [2. DB Lock](#2-db-lock)
        - [DB Lock 종류](#db-lock-종류)
    - [3. Pessimistic Lock](#3-pessimistic-lock)
        - [Pessimistic Lock 장점](#pessimistic-lock-장점)
        - [Pessimistic Lock 단점](#pessimistic-lock-단점)

<br/>

# 작업환경 구성

## 1. DB 세팅

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

## 2. 재고 감소 로직 작성

```java
public void decrease(Long quantity) {
    if (this.quantity < quantity) {
        throw new RuntimeException("재고는 0개 미만이 될 수 없습니다.");
    }

    this.quantity -= quantity;
} 
```

<br/>

## 3. 동시에 요청이 들어올 경우, 발생하는 동시성 이슈 구현

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

# 동시성 이슈 해결 방법 비교

## 1. Synchronized

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

### Synchronized 내용 정리

1. `StockService.decrease()` 메서드 레벨에 `synchronized` 를 작성
    - **이때, `@Transactional`을 사용할 경우 여전히 동시성 이슈가 발생.**
        - 이는 기본 적으로 트랜잭션의 격리 수준이 `READ_COMMITTED`로 동작하기 때문.
        - 조회 후 메모리 상에서 값을 변경하는 동안, 다른 트랜잭션에서 데이터를 수정할 수 있음.
        - 결국 값이 덮어쓰기되는 손실된 갱신(Lost Update) 발생.
2. 따라서 `@Transactional`을 주석처리한 후, `synchronized`를 사용하면 동시성 이슈를 해결 가능.

<br/>

### Synchronized 문제점

- `Synchronized`는 **하나의 프로세스 안에서만 동시성이 보장**.
- 따라서 여러 서버에서 DB에 접근할 경우, 여전히 동시에 데이터에 접근이 가능.
    - → 동시성 이슈가 여전히 발생

<br/>

## 2. DB Lock

### DB Lock 종류

> Pessimistic Lock

- 실제로 데이터에 Lock을 걸어서 정합성을 맞추는 방법
- Exclusive Lock을 걸게되며 다른 트랜잭션에서는 lock 이 해제되기전에 데이터를 가져갈 수 없도록 동작
- **데드락이 발생할 수 있는 위험이 있음.**

<br/>

> Optimistic Lock

- 실제로 Lock을 이용하지 않고 버전을 이용함으로써 정합성을 맞추는 방법
- 먼저 데이터를 읽은 후에 update 를 수행할 때, 현재 내가 읽은 버전이 맞는지 확인하며 업데이트
- 내가 읽은 버전에서 수정사항이 생겼을 경우에는 application에서 다시 읽은후에 작업을 수행이 필요

<br/>

> Named Lock

- 이름을 가진 Metadata Locking
- 이름을 가진 Lock을 획득한 후, 해제할때까지 다른 세션은 이 Lock을 획득할 수 없도록 동작.
- **트랜잭션이 종료될 때 Lock 이 자동으로 해제되지 않으므로, 별도의 명령어로 해제를 수행해주거나 선점시간이 끝나야 해제가 됨.**

<br/>

## 3. Pessimistic Lock

1. `Pessimistic Lock`을 이용해 조회하는 쿼리를 작성
    ```java
    public interface StockRepository extends JpaRepository<Stock, Long> {
        /**
         * Pessimistic Lock 을 이용한 쿼리
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT s FROM Stock s WHERE s.id = :id")
        Stock findByIdWithPessimisticLock(Long id);
    }
    ```
2. `StockServiceTest.재고감소_동시요청2()` 테스트 실행
    - 실제 조회 쿼리를 보면 "for update" 구문이 들어감으로써 Pessimistic Lock임을 확인 가능
    - `Hibernate: select s1_0.id,s1_0.product_id,s1_0.quantity from stock s1_0 where s1_0.id=? for update`

<br/>

### Pessimistic Lock 장점

1. 충돌이 빈번하게 일어난다면 Optimistic Lock 보다 성능이 좋을 순 있음.
2. Lock을 통해 업데이트를 제어하기 때문에 데이터 정합성이 보장됨.

<br/>

### Pessimistic Lock 단점

1. 별도의 Lock을 설정하므로 성능이 떨어질 수 있음.