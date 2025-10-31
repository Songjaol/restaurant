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

    private static final String KAKAO_API_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
    private static final String KAKAO_API_KEY = "KakaoAK c3cb0e2c97616f5fa45e104817c461a2";

    private static final String NAVER_CLIENT_ID = "5ruGnwiw1PdvCM3fXoYw";
    private static final String NAVER_CLIENT_SECRET = "b8mTVdVcfc";
    private static final String GOOGLE_API_KEY = "AIzaSyBZpVKsV34fIBgyWYi_SLizRWYGZiTj9z0";

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
                    .header("Authorization", KAKAO_API_KEY)
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

    private String fetchImageFromGoogle(String keyword) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            // ✅ URL 빌더로 인코딩 문제 방지
            String baseUrl = "https://maps.googleapis.com/maps/api/place/textsearch/json";
            String encodedQuery = URLEncoder.encode(keyword, StandardCharsets.UTF_8).replace("+", "%20");
            String fullUrl = baseUrl + "?query=" + encodedQuery + "&region=kr&key=" + GOOGLE_API_KEY;

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

            JsonNode results = root.path("results");

            if (!results.isArray() || results.size() == 0) {
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


                return photoUrl;
            }

        } catch (Exception e) {

        }
        return null;
    }

    private String fetchImageFromNaver(String name) {
        try {
            String url = "https://openapi.naver.com/v1/search/image?query=" +
                    URLEncoder.encode(name + " 음식 메뉴", StandardCharsets.UTF_8) +
                    "&display=10&sort=sim";

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Naver-Client-Id", NAVER_CLIENT_ID);
            headers.set("X-Naver-Client-Secret", NAVER_CLIENT_SECRET);

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
