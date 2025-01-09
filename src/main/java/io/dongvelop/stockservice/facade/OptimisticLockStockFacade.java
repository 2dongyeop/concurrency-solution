package io.dongvelop.stockservice.facade;

import io.dongvelop.stockservice.service.OptimisticLockStockService;
import org.springframework.stereotype.Component;

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
