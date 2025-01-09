package io.dongvelop.stockservice.service;

import io.dongvelop.stockservice.domain.Stock;
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

    /**
     * 여러 스레드가 동시 자원에 접근하여 값을 변경하려 함.
     * 따라서 동시성 이슈 발생 => 최종 재고 값이 0이 아닌 87, 96 등의 올바르지 않은 값이 도출.
     */
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
}