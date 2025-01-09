package io.dongvelop.stockservice.service;

import io.dongvelop.stockservice.domain.Stock;
import io.dongvelop.stockservice.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PessimisticLockStockService {

    private final StockRepository stockRepository;

    public PessimisticLockStockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    /**
     * Pessimistic Lock 을 이용해 조회함으로써 동시성 이슈를 해결
     */
    @Transactional
    public void decrease(Long id, Long quantity) {
        // Stock 조회
        final Stock stock = stockRepository.findByIdWithPessimisticLock(id);

        // 재고 감소
        stock.decrease(quantity);

        // 갱신된 값을 저장
        stockRepository.save(stock);
    }
}
