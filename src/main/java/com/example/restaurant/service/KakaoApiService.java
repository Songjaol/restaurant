package com.example.restaurant.service;

import com.example.restaurant.entity.Restaurant;
import com.example.restaurant.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${api.kakao.key}")
    private String kakaoApiKey;

    @Value("${api.naver.client-id}")
    private String naverClientId;

    @Value("${api.naver.client-secret}")
    private String naverClientSecret;

    @Value("${api.google.key}")
    private String googleApiKey;
    private static final String KAKAO_API_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";

    /** 지역 이름으로 음식점 수집 및 저장 */
    public void fetchAndSaveRestaurants(String query) {
        synchronized (this) {
            try {
                // “음식점” → “맛집” 자동 변환
                String search = query.contains("음식점")
                        ? query.replace("음식점", "맛집")
                        : query;

                HttpClient client = HttpClient.newHttpClient();
                ObjectMapper mapper = new ObjectMapper();

                List<Restaurant> fetched = fetchFromKakao(search, client, mapper);
                if (fetched.isEmpty()) return;

                // 중복 제거 (name + address 기준)
                Map<String, Restaurant> uniqueMap = new LinkedHashMap<>();
                for (Restaurant r : fetched) {
                    uniqueMap.putIfAbsent(r.getName() + "_" + r.getAddress(), r);
                }

                List<Restaurant> unique = new ArrayList<>(uniqueMap.values());
                List<Restaurant> newOnes = unique.stream()
                        .filter(r -> !restaurantRepository.existsByNameAndAddress(r.getName(), r.getAddress()))
                        .toList();

                if (!newOnes.isEmpty()) {
                    restaurantRepository.saveAll(newOnes);
                    System.out.println("✅ [" + search + "] 새 데이터 " + newOnes.size() + "개 저장");
                }

            } catch (Exception ignored) {}
        }
    }

    /** Kakao API 요청 */
    private List<Restaurant> fetchFromKakao(String query, HttpClient client, ObjectMapper mapper) {
        List<Restaurant> list = new ArrayList<>();
        try {
            String url = KAKAO_API_URL + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", kakaoApiKey)
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode docs = mapper.readTree(response.body()).path("documents");

            if (!docs.isArray()) return list;

            for (JsonNode node : docs) {
                String name = node.path("place_name").asText();
                String address = node.path("address_name").asText();
                if (restaurantRepository.existsByNameAndAddress(name, address)) continue;

                Restaurant r = Restaurant.builder()
                        .name(name)
                        .category(node.path("category_name").asText())
                        .address(address)
                        .phone(node.path("phone").asText(""))
                        .x(node.path("x").asDouble())
                        .y(node.path("y").asDouble())
                        .region(query.replaceAll("\\s+", ""))
                        .placeUrl(node.path("place_url").asText())
                        .imageUrl(fetchImageWithFallback(name))
                        .build();

                list.add(r);
            }

        } catch (Exception ignored) {}

        return list;
    }

    /** 이미지 검색 (Google → Naver → 기본) */
    private String fetchImageWithFallback(String name) {


        String img = fetchImageFromGoogle(name);
        if (img != null) return img;
        img = fetchImageFromNaver(name);
        if (img != null) return img;
        return "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800";
    }

    private String fetchImageFromGoogle(String placeName) {
        try {
            // ✅ 1. API 엔드포인트
            String url = "https://places.googleapis.com/v1/places:searchText";

            // ✅ 2. 요청 본문 — 상호명 단위 검색
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode body = mapper.createObjectNode();
            body.put("textQuery", placeName); // 예: "감성타코 홍대점"
            body.put("languageCode", "ko");
            body.put("regionCode", "KR");

            String requestBody = mapper.writeValueAsString(body);

            // ✅ 3. HTTP 헤더
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("X-Goog-Api-Key", googleApiKey);
            headers.add("X-Goog-FieldMask", "places.photos,places.displayName");

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            RestTemplate restTemplate = new RestTemplate();

            // ✅ 4. Google Places Text Search 호출
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            System.out.println(response.getBody());
            // ✅ 5. 응답 파싱
            JsonNode root = mapper.readTree(response.getBody());
            JsonNode places = root.path("places");

            if (!places.isArray() || places.isEmpty()) {
                System.out.println("⚠️ Google API: [" + placeName + "] 검색 결과 없음");
                return null;
            }

            // ✅ 6. 첫 번째 장소의 사진 정보 가져오기
            JsonNode first = places.get(0);
            JsonNode photos = first.path("photos");

            if (photos.isArray() && photos.size() > 0) {
                String photoName = photos.get(0).path("name").asText();

                // ✅ 7. 최종 이미지 URL 반환 (인코딩 제거)
                String photoUrl = "https://places.googleapis.com/v1/" + photoName +
                        "/media?maxWidthPx=800&key=" + googleApiKey;

                return photoUrl;
            }

        } catch (Exception e) {
            System.out.println("⚠️ Google Places(New) 요청 실패: " + e.getMessage());
        }
        return null;
    }



    private String fetchImageFromNaver(String name) {
        try {
            String url = "https://openapi.naver.com/v1/search/image?query=" +
                    URLEncoder.encode(name + " 음식 메뉴", StandardCharsets.UTF_8) +
                    "&display=10&sort=sim";

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Naver-Client-Id", naverClientId);
            headers.set("X-Naver-Client-Secret", naverClientSecret);

            RestTemplate rest = new RestTemplate();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode items = mapper.readTree(rest.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class
            ).getBody()).path("items");

            for (JsonNode item : items) {
                String link = item.path("link").asText();
                if ((link.endsWith(".jpg") || link.endsWith(".png")) &&
                        !link.contains("blog") && !link.contains("naver.net")) {
                    return link;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
