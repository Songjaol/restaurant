package com.example.restaurant.controller;

import com.example.restaurant.entity.Restaurant;
import com.example.restaurant.repository.RestaurantRepository;
import com.example.restaurant.service.KakaoApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantRepository restaurantRepository;
    private final KakaoApiService kakaoApiService;

    /**
     * ✅ 1️⃣ 전체 맛집 조회
     * 예: http://localhost:8081/restaurants
     */
    @GetMapping
    public List<Restaurant> getAllRestaurants() {
        return restaurantRepository.findAll();
    }

    /**
     * ✅ 2️⃣ 지역 기반 맛집 조회
     * 예: http://localhost:8081/restaurants?region=홍대 맛집
     *
     * - DB에 해당 지역 데이터가 없으면 카카오 API 호출 → 자동 저장 후 반환
     */
    @GetMapping(params = "region")
    public List<Restaurant> getRestaurantsByRegion(@RequestParam String region) throws Exception {
        // 1️⃣ DB에서 해당 지역 맛집 검색
        String normalizedRegion = region.replaceAll("\\s+", "");

        List<Restaurant> list = restaurantRepository.findByNormalizedRegion(normalizedRegion);

        // 2️⃣ DB에 없으면 → 카카오 API로부터 가져오기
        if (list.isEmpty()) {
            System.out.println("⚙️ DB에 [" + normalizedRegion + "] 데이터 없음 → 카카오 API 호출 중...");
            kakaoApiService.fetchAndSaveRestaurants(region); // 원래 검색어(공백 포함)로 API 호출
            list = restaurantRepository.findByNormalizedRegion(normalizedRegion);
        } else {
            System.out.println("💾 DB에서 [" + normalizedRegion + "] 데이터 로드 (" + list.size() + "개)");
        }

        return list;
    }
}
