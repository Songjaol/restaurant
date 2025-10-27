package com.example.restaurant.repository;

import com.example.restaurant.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    boolean existsByNameAndAddress(String name, String address);

    // ✅ 특정 지역으로 조회
    List<Restaurant> findByRegion(String region);
}
