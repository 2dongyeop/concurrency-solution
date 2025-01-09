package io.dongvelop.stockservice.repository;

import io.dongvelop.stockservice.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
