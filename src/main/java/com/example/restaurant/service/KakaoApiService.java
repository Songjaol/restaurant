package com.example.restaurant.service;

import com.example.restaurant.entity.Restaurant;
import com.example.restaurant.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KakaoApiService {

    private final RestaurantRepository restaurantRepository;

    // ✅ 카카오맵 로컬 API URL
    private static final String API_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
    // 🔑 카카오 REST API 키 (발급받은 키로 교체하세요)
    private static final String API_KEY = "KakaoAK yourkey";

    public void fetchAndSaveRestaurants(String query) throws Exception {
        // 1️⃣ API 요청 URL 구성
        String url = API_URL + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

        // 2️⃣ HTTP 요청 전송
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", API_KEY)
                .build();

        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());


        System.out.println("====================================");
        System.out.println("📡 요청 지역: " + query);
        System.out.println("📡 상태 코드: " + response.statusCode());
        System.out.println("📡 응답 본문: " + response.body());
        System.out.println("====================================");

        // 3️⃣ JSON 파싱
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body());
        JsonNode docs = root.get("documents");

        // ✅ 방어 코드 추가 (docs가 null일 경우 바로 리턴)
        if (docs == null || !docs.isArray()) {
            System.out.println("⚠️ documents가 null입니다. 카카오 API 응답 확인 필요.");
            System.out.println("💡 API 키 형식 또는 플랫폼 설정을 확인하세요.");
            return;
        }
        List<Restaurant> list = new ArrayList<>();

        for (JsonNode node : docs) {
            String name = node.get("place_name").asText();
            String address = node.get("address_name").asText();

            // 중복 방지
            if (restaurantRepository.existsByNameAndAddress(name, address)) continue;

            Restaurant r = Restaurant.builder()
                    .name(name)
                    .category(node.get("category_name").asText())
                    .address(address)
                    .phone(node.get("phone").asText())
                    .x(node.get("x").asDouble())
                    .y(node.get("y").asDouble())
                    .region(query)
                    .placeUrl(node.get("place_url").asText())
                    .build();

            list.add(r);
        }

        restaurantRepository.saveAll(list);
        System.out.println("✅ [" + query + "] 지역 맛집 " + list.size() + "개 저장 완료");
    }
}
