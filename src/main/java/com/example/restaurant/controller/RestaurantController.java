package com.example.restaurant.controller;

import com.example.restaurant.entity.Restaurant;
import com.example.restaurant.repository.RestaurantRepository;
import com.example.restaurant.service.KakaoApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantRepository restaurantRepository;
    private final KakaoApiService kakaoApiService;

    // 단일 스레드 풀 — 중복 insert 방지
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** 전체 맛집 조회 */
    @GetMapping
    public List<Restaurant> getAll() {
        return restaurantRepository.findAll();
    }

    /** 지역별 맛집 조회 (없으면 비동기로 수집) */
    @GetMapping(params = "region")
    public List<Restaurant> getRestaurantsByRegion(@RequestParam String region) {
        String normalizedRegion = region.replaceAll("\\s+", "");
        if (normalizedRegion.contains("음식점")) {
            normalizedRegion = normalizedRegion.replace("음식점", "맛집");
            System.out.println("🔁 검색어 변환됨: " + region + " → " + normalizedRegion);
        }
        List<Restaurant> list = restaurantRepository.findByNormalizedRegion(normalizedRegion);

        if (list.isEmpty()) {
            executor.submit(() -> {
                try {
                    kakaoApiService.fetchAndSaveRestaurants(region);
                } catch (Exception ignored) {}
            });
            return List.of(); // 프론트 로딩용
        }

        System.out.println("📦 [" + normalizedRegion + "] DB 로드: " + list.size() + "개");
        return list;
    }
}
