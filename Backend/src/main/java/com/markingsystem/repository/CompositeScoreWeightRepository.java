package com.markingsystem.repository;

import com.markingsystem.entity.CompositeScoreWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompositeScoreWeightRepository extends JpaRepository<CompositeScoreWeight, Long> {
}
