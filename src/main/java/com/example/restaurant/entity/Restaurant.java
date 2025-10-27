package com.example.restaurant.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;        // 장소 이름
    private String category;    // 카테고리
    private String address;     // 주소
    private String phone;       // 전화번호
    private double x;           // 경도
    private double y;           // 위도
    private String region;      // 검색 지역명 (예: 홍대 맛집)
    @Column
    private String placeUrl;

}
