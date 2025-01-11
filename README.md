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
        - [2-1. DB Lock 종류](#2-1-db-lock-종류)
        - [2-2. Pessimistic Lock](#2-2-pessimistic-lock)
            - [Pessimistic Lock 장점](#pessimistic-lock-장점)
            - [Pessimistic Lock 단점](#pessimistic-lock-단점)
        - [2-3. Optimistic Lock](#2-3-optimistic-lock)
            - [Optimistic Lock 장점](#optimistic-lock-장점)
            - [Optimistic Lock 단점](#optimistic-lock-단점)
        - [2-4. Named Lock](#2-4-named-lock)
            - [Named Lock 장점](#named-lock-장점)
            - [Named Lock 단점](#named-lock-단점)

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

### 2-1. DB Lock 종류

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

## 2-2. Pessimistic Lock

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

<br/>

## 2-3. Optimistic Lock

1. `Version` 컬럼 추가
    ```java
    @Entity
    public class Stock {
        @jakarta.persistence.Version
        private Long version;
    }
    ```

2. `Optimistic Lock`을 이용해 조회하는 쿼리 작성
    ```java
    public interface StockRepository extends JpaRepository<Stock, Long> {
        /**
         * Optimistic Lock 을 이용한 쿼리
         */
        @Lock(LockModeType.OPTIMISTIC)
        @Query("SELECT s FROM Stock s WHERE s.id = :id")
        Stock findByIdWithOptimisticLock(Long id);
    }
    ```

3. `Version` 충돌이 발생할 경우, 다시 조회 후 업데이트하도록 동작하는 `Facade` 작성
    ```java
    @Component
    public class OptimisticLockStockFacade {
    
        private final OptimisticLockStockService optimisticLockStockService;
    
        public OptimisticLockStockFacade(OptimisticLockStockService optimisticLockStockService) {
            this.optimisticLockStockService = optimisticLockStockService;
        }
    
        public void decrease(Long id, Long quantity) throws InterruptedException {
            while (true) {
                try {
                    optimisticLockStockService.decrease(id, quantity);
    
                    // 정상적으로 업데이트가 되었다면 break
                    break;
                } catch (Exception e) {
                    // Version이 달라 업데이트에 실패한 경우, 50ms 지연시간 후에 다시 조회 후 업데이트. 
                    Thread.sleep(50);
                }
            }
        }
    }
    ``` 

4. `StockServiceTest.재고감소_동시요청3()` 테스트 실행

<br/>

### Optimistic Lock 단점

1. 버전 충돌시 재시도 로직으로 인한 소요 시간 증가
2. 버전 충돌시 재시도 로직을 개발자가 직접 작성하는 번거로움이 발생

<br/>

### Optimistic Lock 장점

1. 별도의 `Lock`을 설정하지 않으므로, `Pessimistic Lock` 보다 성능이 좋음
2. 충돌이 빈번하게 발생하지 않는다고 예상이 갈 경우에 적합.

<br/>

## 2-4. Named Lock

1. `Lock`을 관리하는 `Repository` 작성
    ```java
    /**
     * 실무에서는 Stock Entity를 그대로 사용하지 말고, 별도의 jdbc를 사용하는 방식으로 분리를 권장.
     * 같은 DataSource를 사용할 경우, 커넥션 풀이 부족해지는 현상으로 다른 서비스에 영향을 끼칠 수 있음.
     */
    public interface LockRepository extends JpaRepository<Stock, Long> {
        /**
         * MySQL에서 Lock을 획득하는 GET_LOCK
         */
        @Query(value = "SELECT GET_LOCK(:key, 3000)", nativeQuery = true)
        void getLock(String key);
    
        /**
         * MySQL에서 Lock을 해제하는 RELEASE_LOCK
         */
        @Query(value = "SELECT RELEASE_LOCK(:key)", nativeQuery = true)
        void releaseLock(String key);
    }
    ```
2. `Lock`을 얻고 해지하는 `Facade` 작성
    ```java
    @Component
    public class NamedLockStockFacade {
        private final NamedLockStockService namedLockStockService;
        private final LockRepository lockRepository;
    
        public NamedLockStockFacade(NamedLockStockService namedLockStockService, LockRepository lockRepository) {
            this.namedLockStockService = namedLockStockService;
            this.lockRepository = lockRepository;
        }
    
        public void decrease(Long id, Long quantity) {
            try {
                // Lock 획득
                lockRepository.getLock(id.toString());
    
                namedLockStockService.decrease(id, quantity);
            } finally {
                // Lock 해지
                lockRepository.releaseLock(id.toString());
            }
        }
    }
    ```
3. 부모와 별도의 트랜잭션으로 동작하도록 `propagation` 설정 추가
    ```java
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decrease(Long id, Long quantity) {
        // 기존과 동일 
    }
    ```
4. Database Connection Pool 조정
    ```yaml
    spring:
      datasource:
        hikari:
          maximum-pool-size: 40
    ```

5. `StockServiceTest.재고감소_동시요청4()` 테스트 실행

<br/>

### Named Lock 단점

1. 트랜잭션 종료 시에 `Lock` 해지 및 세션 관리가 필요.
2. 실제로 사용할 때에는 구현 방법이 복잡할 수 있음.

<br/>

### Named Lock 장점

1. 주로 분산락을 구현할 때 사용
2. 타임아웃을 구현하기 쉬움(`Pessimistic Lock`은 타임아웃을 구현하기 힘듬.)

<br/>

## 3. Redis Lock

> Redisson

- Pub/Sub 기반으로 Lock 구현 제공

### Redis 환경 세팅

1. Docker Container 기동
    ```shell
    docker pull redis
    docker run --name myredis -d -p 6379:6379 redis
    ```

2. Redis 의존성 추가
    ```groovy
    dependencies {
        implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    }
    ```

3. Redis CLI 동작 확인
    ```shell
    # Redis CLI 접속
    docker exec -it ${CONTAINER ID} redis-cli
    
    # 값 삽입
    127.0.0.1:6379> setnx 1 lock
    (integer) 1    # 1개의 값이 들어감
    
    127.0.0.1:6379> setnx 1 lock
    (integer) 0    # 0개의 값이 들어감 => 이미 존재하므로.
    
    # 값 삭제
    127.0.0.1:6379> del 1
    (integer) 1
    ```

<br/>

### Lettuce Lock 구현

> `setnx` 명령을 활용하여 분산락 구현
> → `set if not exist` 줄임말로, 기존의 키-값이 없을 때에만 동작하는 방식

1. RedisLockRepository 구현
    ```java
    @Component
    public class RedisLockRepository {
    
        private final RedisTemplate<String, String> redisTemplate;
    
        public RedisLockRepository(RedisTemplate<String, String> redisTemplate) {
            this.redisTemplate = redisTemplate;
        }
    
        public Boolean lock(Long key) {
            return redisTemplate.opsForValue()
                    .setIfAbsent(generateKey(key), "lock", Duration.ofMillis(3_000));
        }
    
        public Boolean unlock(Long key) {
            return redisTemplate.delete(generateKey(key));
        }
    
        private String generateKey(Long key) {
            return key.toString();
        }
    }
    ```

2. LettuceLockStockFacade 작성
    ```java
    @Component
    public class LettuceLockStockFacade {
    
        private final RedisLockRepository redisLockRepository;
        private final StockService stockService;
    
        public LettuceLockStockFacade(RedisLockRepository redisLockRepository, StockService stockService) {
            this.redisLockRepository = redisLockRepository;
            this.stockService = stockService;
        }
    
        public void decrease(Long id, Long quantity) throws InterruptedException {
            // 락을 획득할 때까지 100ms Sleep. => Redis 부하 줄이기
            while (!redisLockRepository.lock(id)) {
                Thread.sleep(100);
            }
    
            // 락 획득시
            try {
                stockService.decrease(id, quantity);
            } finally {
                redisLockRepository.unlock(id);
            }
        }
    }
    ```

3. `StockServiceTest.재고감소_동시요청5()` 테스트 실행

<br/>

### Lettuce Lock 단점

- Spin Lock 방식
    - 개발자가 직접 Retry 로직을 작성해야 함
    - 이 과정에서 Redis에 부하가 생길 수 있음.

### Lettuce Lock 장점

- 구현이 간단하다.
- MySQL의 Named Lock과 비슷한 동작 방식
    - 세션 관리를 하지 않아도 된다는 이점