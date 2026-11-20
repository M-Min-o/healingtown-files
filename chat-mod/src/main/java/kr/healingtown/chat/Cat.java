package kr.healingtown.chat;

import java.util.LinkedHashMap;
import java.util.Map;

/** 채팅 종류 (서버 /채팅설정 과 같은 키) */
public final class Cat {
    private Cat() {}

    public static final String ALL = "all";
    public static final String[] IDS = {"general", "whisper", "party", "brag", "notice", "money", "item", "join", "system"};
    public static final Map<String, String> KOR = new LinkedHashMap<>();
    static {
        KOR.put("general", "일반");
        KOR.put("whisper", "귓속말");
        KOR.put("party", "파티");
        KOR.put("brag", "자랑");
        KOR.put("notice", "공지");
        KOR.put("money", "골드·경험치");
        KOR.put("item", "아이템");
        KOR.put("join", "접속·퇴장");
        KOR.put("system", "시스템");
    }

    /** 탭 순서 (이미지처럼 전체 먼저) */
    public static final String[] TABS = {ALL, "general", "whisper", "party", "system", "money", "item", "brag", "notice", "join"};

    public static String name(String id) {
        return ALL.equals(id) ? "전체" : KOR.getOrDefault(id, id);
    }
}
