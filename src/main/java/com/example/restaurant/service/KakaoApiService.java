package com.example.restaurant.service;

import com.example.restaurant.entity.Restaurant;
import com.example.restaurant.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class KakaoApiService {

    private final RestaurantRepository restaurantRepository;

    // ✅ API 키 설정
    private static final String KAKAO_API_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
    private static final String KAKAO_API_KEY = "KakaoAK c3cb0e2c97616f5fa45e104817c461a2";

    private static final String NAVER_CLIENT_ID = "5ruGnwiw1PdvCM3fXoYw";
    private static final String NAVER_CLIENT_SECRET = "b8mTVdVcfc";
    private static final String GOOGLE_API_KEY = "AIzaSyBZpVKsV34fIBgyWYi_SLizRWYGZiTj9z0";

    /**
     * ✅ 지역 이름으로 음식점 정보 수집 및 저장
     */
    public void fetchAndSaveRestaurants(String query) throws Exception {
        System.out.println("🔍 [Kakao API 호출] 검색어: " + query);

        // ‘맛집’ / ‘음식점’ 두 버전으로 검색
        Set<String> queries = new LinkedHashSet<>();
        queries.add(query);
        if (query.contains("음식점")) queries.add(query.replace("음식점", "맛집"));
        else if (query.contains("맛집")) queries.add(query.replace("맛집", "음식점"));

        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();
        List<Restaurant> totalList = new ArrayList<>();

        // Kakao API 요청 (순차 처리)
        for (String q : queries) {
            totalList.addAll(fetchFromKakao(q, client, mapper));
        }

        // DB 저장
        if (!totalList.isEmpty()) {
            restaurantRepository.saveAll(totalList);
            System.out.println("✅ [" + query + "] 총 " + totalList.size() + "개 저장 완료");
        } else {
            System.out.println("❌ [" + query + "] 검색 결과 없음");
        }
    }

    /**
     * 📡 Kakao API 요청 및 파싱
     */
    private List<Restaurant> fetchFromKakao(String q, HttpClient client, ObjectMapper mapper) {
        List<Restaurant> list = new ArrayList<>();

        try {
            String url = KAKAO_API_URL + "?query=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", KAKAO_API_KEY)
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            JsonNode docs = root.path("documents");

            if (!docs.isArray() || docs.size() == 0) {
                System.out.println("⚠️ [" + q + "] 결과 없음");
                return list;
            }

            for (JsonNode node : docs) {
                String name = node.path("place_name").asText();
                String address = node.path("address_name").asText();

                // 이미 DB에 존재하면 스킵
                if (restaurantRepository.existsByNameAndAddress(name, address)) continue;

                // ✅ Google → Naver → 기본 이미지 순서로 이미지 가져오기
                String imageUrl = fetchImageWithFallback(name);

                Restaurant r = Restaurant.builder()
                        .name(name)
                        .category(node.path("category_name").asText())
                        .address(address)
                        .phone(node.path("phone").asText(""))
                        .x(node.path("x").asDouble())
                        .y(node.path("y").asDouble())
                        .region(q.replaceAll("\\s+", "")) // 공백 제거
                        .placeUrl(node.path("place_url").asText())
                        .imageUrl(imageUrl)
                        .build();

                list.add(r);
            }

        } catch (Exception e) {
            System.out.println("❌ [" + q + "] 요청 실패: " + e.getMessage());
        }

        return list;
    }

    /**
     * 🖼️ Google → Naver → 기본 이미지 fallback
     */
    private String fetchImageWithFallback(String restaurantName) {
        String imageUrl = fetchImageFromGoogle(restaurantName);
        if (imageUrl != null) return imageUrl;

        imageUrl = fetchImageFromNaver(restaurantName);
        if (imageUrl != null) return imageUrl;

        return "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800";
    }

    /**
     * 🟢 Google Places API
     */
    private String fetchImageFromGoogle(String keyword) {
        try {
            System.out.println("\n🔍 [Google API 호출 시작] 검색어: " + keyword);

            ObjectMapper mapper = new ObjectMapper();

            // ✅ URL 빌더로 인코딩 문제 방지
            String baseUrl = "https://maps.googleapis.com/maps/api/place/textsearch/json";
            String encodedQuery = URLEncoder.encode(keyword, StandardCharsets.UTF_8).replace("+", "%20");
            String fullUrl = baseUrl + "?query=" + encodedQuery + "&region=kr&key=" + GOOGLE_API_KEY;

            System.out.println("📡 요청 URL: " + fullUrl);

            // ✅ RestTemplate exchange()로 요청 (String으로 직접 받기)
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    URI.create(fullUrl),
                    HttpMethod.GET,
                    null,
                    String.class
            );

            String searchResponse = responseEntity.getBody();
            JsonNode root = mapper.readTree(searchResponse);
            System.out.println("🔍 [Google API 전체 응답]\n" +
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));

            JsonNode results = root.path("results");

            if (!results.isArray() || results.size() == 0) {
                System.out.println("⚠️ Google API: 결과 없음 (" + keyword + ")");
                return null;
            }

            JsonNode first = results.get(0);
            JsonNode photos = first.path("photos");

            if (photos.isArray() && photos.size() > 0) {
                String photoRef = photos.get(0).path("photo_reference").asText();
                String photoUrl = "https://maps.googleapis.com/maps/api/place/photo"
                        + "?maxwidth=800"
                        + "&photo_reference=" + photoRef
                        + "&key=" + GOOGLE_API_KEY;

                System.out.println("✅ Google 이미지 URL 생성됨: " + photoUrl);
                return photoUrl;
            }

        } catch (Exception e) {
            System.out.println("❌ Google 이미지 요청 실패: " + e.getMessage());
        }
        return null;
    }


    /**
     * 🟡 Naver Image API
     */
    private String fetchImageFromNaver(String restaurantName) {
        try {
            String keyword = restaurantName + " 음식 메뉴";
            String url = "https://openapi.naver.com/v1/search/image?query=" +
                    URLEncoder.encode(keyword, StandardCharsets.UTF_8) +
                    "&display=10&sort=sim";

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Naver-Client-Id", NAVER_CLIENT_ID);
            headers.set("X-Naver-Client-Secret", NAVER_CLIENT_SECRET);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody());
            JsonNode items = root.path("items");

            if (items.isArray() && items.size() > 0) {
                for (JsonNode item : items) {
                    String link = item.path("link").asText();
                    if ((link.endsWith(".jpg") || link.endsWith(".png")) &&
                            !link.contains("blog") && !link.contains("adcr") && !link.contains("naver.net")) {
                        return link;
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("⚠️ Naver 이미지 요청 실패: " + e.getMessage());
        }
        return null;
    }
}
