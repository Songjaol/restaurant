//package com.example.restaurant.config;
//
//import com.example.restaurant.service.KakaoApiService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Component;
//
//import jakarta.annotation.PostConstruct;
//import java.util.List;
//
//@Component
//@RequiredArgsConstructor
//public class AutoFetcher {
//
//    private final KakaoApiService kakaoApiService;
//
//    @PostConstruct
//    public void autoFetch() throws Exception {
//        // ✅ 자동으로 여러 지역의 맛집 데이터를 불러와 DB에 저장
//        List<String> regions = List.of("홍대 맛집", "강남 맛집", "부산 맛집", "대구 맛집");
//
//        for (String region : regions) {
//            kakaoApiService.fetchAndSaveRestaurants(region);
//            Thread.sleep(2000); // API 호출 간 간격 (2초)
//        }
//
//        System.out.println("🎉 모든 지역의 맛집 데이터 저장 완료!");
//    }
//}
