package io.dongvelop.stockservice.service;

import io.dongvelop.stockservice.domain.Stock;
import io.dongvelop.stockservice.facade.LettuceLockStockFacade;
import io.dongvelop.stockservice.facade.NamedLockStockFacade;
import io.dongvelop.stockservice.facade.OptimisticLockStockFacade;
import io.dongvelop.stockservice.facade.RedissonLockStockFacade;
import io.dongvelop.stockservice.repository.StockRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StockServiceTest {

    @Autowired
    private StockService stockService;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private PessimisticLockStockService pessimisticLockStockService;
    @Autowired
    private OptimisticLockStockFacade optimisticLockStockFacade;
    @Autowired
    private NamedLockStockFacade namedLockStockFacade;
    @Autowired
    private LettuceLockStockFacade lettuceLockStockFacade;
    @Autowired
    private RedissonLockStockFacade redissonLockStockFacade;

    @BeforeEach
    public void beforeEach() {
        stockRepository.save(new Stock(1L, 100L));
    }

    @AfterEach
    public void afterEach() {
        stockRepository.deleteAll();
    }

    @Test
    @DisplayName("재고 감소 요청이 1건이 들어왔을 경우, 재고가 1만큼 감소해야 한다.")
    void 재고감소_단건요청() throws Exception {

        // given
        final Long id = 1L;
        final Long quantity = 1L;

        // when
        stockService.decrease(id, quantity);

        // then
        final Stock stock = stockRepository.findById(id).orElseThrow();
        Assertions.assertThat(stock.getQuantity()).isEqualTo(99);
    }

    @Test
    @DisplayName("[Synchoronized]재고 감소 요청이 동시에 n건이 들어왔을 경우, 재고가 n만큼 감소해야 한다.")
    void 재고감소_동시요청1() throws Exception {

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

    @Test
    @DisplayName("[Pessimistic Lock]재고 감소 요청이 동시에 n건이 들어왔을 경우, 재고가 n만큼 감소해야 한다.")
    void 재고감소_동시요청2() throws Exception {

        /**
         * 실제 조회 쿼리를 보면 아래와 같이 "for update" 구문이 들어감. = Pessimistic Lock
         * Hibernate: select s1_0.id,s1_0.product_id,s1_0.quantity from stock s1_0 where s1_0.id=? for update
         */

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
                    pessimisticLockStockService.decrease(id, quantity);
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

    @Test
    @DisplayName("[Optimistic Lock]재고 감소 요청이 동시에 n건이 들어왔을 경우, 재고가 n만큼 감소해야 한다.")
    void 재고감소_동시요청3() throws Exception {

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
                    try {
                        optimisticLockStockFacade.decrease(id, quantity);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
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

    @Test
    @DisplayName("[Named Lock]재고 감소 요청이 동시에 n건이 들어왔을 경우, 재고가 n만큼 감소해야 한다.")
    void 재고감소_동시요청4() throws Exception {

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
                    namedLockStockFacade.decrease(id, quantity);
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

    @Test
    @DisplayName("[Redis Lettuce Lock] 재고 감소 요청이 동시에 n건이 들어왔을 경우, 재고가 n만큼 감소해야 한다.")
    void 재고감소_동시요청5() throws Exception {

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
                    lettuceLockStockFacade.decrease(id, quantity);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
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

    @Test
    @DisplayName("[Redis Redisson Lock] 재고 감소 요청이 동시에 n건이 들어왔을 경우, 재고가 n만큼 감소해야 한다.")
    void 재고감소_동시요청6() throws Exception {

        // given
        final Long id = 1L;
        final Long quantity = 1L;
        final int threadCount = 100;
        final ExecutorService executorService = Executors.newFixedThreadPool(32);
        final CountDownLatch countDownLatch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    redissonLockStockFacade.decrease(id, quantity);
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
}