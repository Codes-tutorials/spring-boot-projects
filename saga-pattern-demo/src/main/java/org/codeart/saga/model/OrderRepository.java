package org.codeart.saga.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByCustomerId(String customerId);

    List<Order> findBySagaStatus(SagaStatus status);
}
