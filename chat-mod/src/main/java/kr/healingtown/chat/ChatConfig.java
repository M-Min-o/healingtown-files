package kr.healingtown.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 채팅창 설정 (.minecraft/config/healingtown-chat.json)
 *  위치 · 크기는 화면 왼쪽 아래 기준 (GUI 배율 단위) - 창 크기가 바뀌어도 화면 안으로 맞춤
 */
public class ChatConfig {
    public boolean enabled = true;
    public int left = 2;
    public int bottom = 40;
    public int width = 330;
    public int height = 170;
    /** 배경 불투명도 0 ~ 1 */
    public float opacity = 0.62f;
    /** 글자 크기 0.5 ~ 2 */
    public float textScale = 1.0f;
    /** 채팅창을 닫았을 때 새 채팅이 보이는 시간 (초) */
    public int fadeSeconds = 8;
    /** 채팅창을 닫아도 창 (배경 · 탭) 을 항상 보임 */
    public boolean alwaysShow = false;
    public boolean showTime = true;
    public boolean locked = false;
    /** 종류마다 기억하는 채팅 수 */
    public int historyPerCategory = 500;
    public String tab = Cat.ALL;
    /** 전체 탭에 보일 종류 */
    public Map<String, Boolean> inAll = new LinkedHashMap<>();
    /** 시스템 메시지 → 종류 (색 코드 뺀 글자, 정규식) - 서버 TotalSystemPlugin chat-channels.rules 와 같은 기본값 */
    public Map<String, List<String>> rules = new LinkedHashMap<>();

    private transient Map<String, List<Pattern>> compiled;

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("healingtown-chat.json");
    }

    public static ChatConfig load() {
        ChatConfig c = null;
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                c = GSON.fromJson(r, ChatConfig.class);
            } catch (Exception e) {
                HtChat.LOG.warn("[힐링타운 채팅] 설정 파일을 읽지 못해 기본값을 씁니다: " + e);
            }
        }
        if (c == null) c = new ChatConfig();
        c.fill();
        c.save();
        return c;
    }

    /** 빠진 값 채우기 (예전 설정 파일 · 새 종류) */
    void fill() {
        if (inAll == null) inAll = new LinkedHashMap<>();
        for (String id : Cat.IDS) inAll.putIfAbsent(id, true);
        if (rules == null) rules = new LinkedHashMap<>();
        Map<String, List<String>> def = defaultRules();
        for (Map.Entry<String, List<String>> en : def.entrySet()) rules.putIfAbsent(en.getKey(), en.getValue());
        if (tab == null || (!tab.equals(Cat.ALL) && !Cat.KOR.containsKey(tab))) tab = Cat.ALL;
        opacity = clamp(opacity, 0f, 1f);
        textScale = clamp(textScale, 0.5f, 2f);
        fadeSeconds = Math.max(1, Math.min(60, fadeSeconds));
        historyPerCategory = Math.max(50, Math.min(2000, historyPerCategory));
        width = Math.max(MIN_W, width);
        height = Math.max(MIN_H, height);
        compiled = null;
    }

    static final int MIN_W = 160, MIN_H = 70;

    static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    public void save() {
        try {
            Files.createDirectories(path().getParent());
            try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (Exception e) {
            HtChat.LOG.warn("[힐링타운 채팅] 설정을 저장하지 못했습니다: " + e);
        }
    }

    Map<String, List<Pattern>> patterns() {
        if (compiled != null) return compiled;
        Map<String, List<Pattern>> m = new LinkedHashMap<>();
        for (String id : Cat.IDS) {
            List<String> src = rules.get(id);
            if (src == null) continue;
            List<Pattern> l = new ArrayList<>();
            for (String s : src) {
                try { l.add(Pattern.compile(s)); } catch (Exception e) { HtChat.LOG.warn("[힐링타운 채팅] 규칙이 잘못되었습니다 (" + id + "): " + s); }
            }
            if (!l.isEmpty()) m.put(id, l);
        }
        compiled = m;
        return m;
    }

    static Map<String, List<String>> defaultRules() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("general", new ArrayList<>());
        m.put("whisper", new ArrayList<>(List.of(
                "\\[\\s*\\S+\\s*(->|→|➜|»)\\s*\\S+\\s*\\]",
                "^\\S+\\s*(->|→|➜)\\s*\\S+\\s*:",
                "귓속말",
                "whispers to you|You whisper to")));
        m.put("party", new ArrayList<>(List.of("^\\[파티\\]")));
        m.put("brag", new ArrayList<>(List.of("^\\[자랑\\]")));
        m.put("notice", new ArrayList<>(List.of("^\\[\\s*공지\\s*\\]", "^\\[\\s*핫타임\\s*\\]")));
        m.put("money", new ArrayList<>(List.of("[0-9][0-9,]*(\\.[0-9]+)?\\s*원", "경험치\\s*\\+", "경험치를\\s*(얻|획득|받)")));
        m.put("item", new ArrayList<>(List.of("보상:", "획득", "받았습니다")));
        m.put("join", new ArrayList<>(List.of("님이\\s*(접속|퇴장|입장)", "(joined|left) the game", "게임에 (참여|나갔)")));
        m.put("system", new ArrayList<>());
        return m;
    }
}
