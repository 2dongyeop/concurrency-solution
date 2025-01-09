package io.dongvelop.stockservice.repository;


import io.dongvelop.stockservice.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {
}
