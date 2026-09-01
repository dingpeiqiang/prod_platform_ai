package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.OpsWorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface OpsWorkOrderRepository
        extends JpaRepository<OpsWorkOrder, Long>, JpaSpecificationExecutor<OpsWorkOrder> {

    Optional<OpsWorkOrder> findByWorkOrderId(String workOrderId);

    List<OpsWorkOrder> findTop50ByOrderByCreatedAtDesc();

    List<OpsWorkOrder> findByOfferingIdOrderByCreatedAtDesc(String offeringId);

    List<OpsWorkOrder> findTop50ByStatusOrderByCreatedAtDesc(String status);

    List<OpsWorkOrder> findTop50BySessionIdOrderByCreatedAtDesc(String sessionId);

    List<OpsWorkOrder> findTop50BySessionIdAndStatusOrderByCreatedAtDesc(String sessionId, String status);

    long countByStatus(String status);
}
