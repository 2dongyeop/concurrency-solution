package io.dongvelop.stockservice.facade;

import io.dongvelop.stockservice.service.StockService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedissonLockStockFacade {

    private final RedissonClient redissonClient;
    private final StockService stockService;

    public RedissonLockStockFacade(RedissonClient redissonClient, StockService stockService) {
        this.redissonClient = redissonClient;
        this.stockService = stockService;
    }

    public void decrease(Long id, Long quantity) {

        // Lock 객체 생성
        final RLock lock = redissonClient.getLock(id.toString());

        try {
            // 10번간 락을 얻기 위해 시도, 이후에 얻을 경우 1초간 점유
            boolean available = lock.tryLock(15, 1, TimeUnit.SECONDS);

            // 락 획득 실패시
            if (!available) {
                System.err.println("Lock 획득 실패");
            }

            // 락 획득 성공시
            stockService.decrease(id, quantity);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 작업이 모두 끝나면 Lock 해제
            lock.unlock();
        }
    }
}
