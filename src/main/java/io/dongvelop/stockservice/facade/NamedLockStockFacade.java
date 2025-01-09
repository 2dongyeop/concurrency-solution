package io.dongvelop.stockservice.facade;

import io.dongvelop.stockservice.repository.LockRepository;
import io.dongvelop.stockservice.service.NamedLockStockService;
import org.springframework.stereotype.Component;

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
