package com.catalog.repository;

import com.catalog.domain.Application;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    Optional<Application> findByExternalApplicationId(String externalApplicationId);

    Page<Application> findAllByOrderByNameAsc(Pageable pageable);
}
