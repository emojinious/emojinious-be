package com.emojinious.emojinious_backend.service;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RandomWordGenerator {
    private final List<String> words = Arrays.asList(
            "사과", "바나나", "고양이", "강아지", "코끼리", "비행기", "자동차", "컴퓨터", "전화기", "책",
            "연필", "모자", "신발", "바다", "산", "꽃", "나무", "태양", "달", "별",
            "커피", "피자", "치킨", "아이스크림", "초콜릿", "음악", "영화", "춤", "운동", "여행"
    );

    public Map<String, String> generateKeywords(int count) {
        Map<String, String> result = new HashMap<>();
        List<String> shuffledWords = new ArrayList<>(words);
        Collections.shuffle(shuffledWords);

        for (int i = 0; i < count; i++) {
            result.put("player" + i, shuffledWords.get(i));
        }

        return result;
    }
}