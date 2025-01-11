package io.dongvelop.stockservice.facade;

import io.dongvelop.stockservice.repository.RedisLockRepository;
import io.dongvelop.stockservice.service.StockService;
import org.springframework.stereotype.Component;

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
