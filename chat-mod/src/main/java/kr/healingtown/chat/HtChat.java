package kr.healingtown.chat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 힐링타운 채팅창
 *  - 바닐라 채팅 대신 옮기고 크기를 바꿀 수 있는 채팅창 (채팅을 열면 위 탭 · 자물쇠 · 톱니바퀴)
 *  - 탭: 전체 · 일반 · 귓속말 · 파티 · 시스템 · 골드·경험치 · 아이템 · 자랑 · 공지 · 접속·퇴장
 *  - 설정 (톱니바퀴): 전체 탭에 보일 종류 · 배경 불투명도 · 글자 크기 · 표시 시간 · 항상 표시 · 시간 표시 · 위치 잠금 · 종류별 기록 개수
 *  - 플레이어 채팅 = 일반, 시스템 메시지는 config 의 rules (정규식) 로 종류를 나눔 (서버 /채팅설정 과 같은 기본값)
 */
public class HtChat implements ClientModInitializer {

    public static final Logger LOG = LoggerFactory.getLogger("htchat");
    public static ChatConfig cfg;
    public static final ChatStore store = new ChatStore();
    public static ChatWindow window;
    /** 바로 다음에 채팅창에 들어오는 메시지가 플레이어 채팅인지 (Fabric 채팅 받음 이벤트 → ChatHud.addMessage) */
    static boolean nextGeneral;

    @Override
    public void onInitializeClient() {
        cfg = ChatConfig.load();
        window = new ChatWindow();

        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> nextGeneral = true);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> { if (!overlay) nextGeneral = false; });

        HudRenderCallback.EVENT.register((ctx, tick) -> window.renderHud(ctx));

        // 채팅 화면: 창 그리기 · 마우스 (Fabric 은 화면을 다시 init 할 때 이 이벤트들을 새로 만듦 → 매번 등록)
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof ChatScreen) || !active()) return;
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> window.renderFocused(ctx, mx, my));
            ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, button) -> !window.mouseDown(s, mx, my, button));
            ScreenMouseEvents.allowMouseRelease(screen).register((s, mx, my, button) -> { window.mouseUp(); return true; });
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, h2, v) -> !window.scroll(mx, my, v));
            ScreenEvents.remove(screen).register(s -> window.closed());
        });
        LOG.info("[힐링타운 채팅] 켜짐");
    }

    public static boolean active() {
        return cfg != null && cfg.enabled;
    }

    /** 미니맵 모드 제어 코드 (Xaero: 미니맵 끄기 · 공정 모드 / VoxelMap) */
    static boolean isMapCode(String raw) {
        return raw.contains("§n§o§m§i§n§i§m§a§p") || raw.contains("§f§a§i§r§x§a§e§r§o")
                || raw.contains("§r§e§s§e§t§x§a§e§r§o") || raw.contains("§3 §6 §3 §6 §3 §6");
    }

    /** 빈 줄이 연달아 이만큼 오면 = 서버의 채팅 청소 (/지우개 등: 빈 줄 100개) → 채팅창을 진짜로 비움 */
    static final int CLEAR_RUN = 20;
    static int blankRun;

    /** ChatHud.addMessage (모든 채팅 · 시스템 메시지 · 클라이언트 메시지) */
    public static void onMessage(Text message) {
        if (cfg == null || message == null) return;
        String raw = message.getString();
        if (!nextGeneral && isMapCode(raw)) return;                     // 서버가 보내는 미니맵 막기 코드 (화면에 안 보이는 글자) - 빈 줄로도 안 띄움
        String plain = raw.replaceAll("§.", "");
        if (!nextGeneral && cfg.hidden(plain)) return;                  // 숨김 (모드 새 버전 알림 등)
        if (plain.isBlank() && !nextGeneral) {
            blankRun++;
            if (blankRun == CLEAR_RUN) { store.clear(); if (window != null) window.onNew(); }
            if (blankRun >= CLEAR_RUN) return;                         // 청소용 빈 줄은 더 쌓지 않음
        } else blankRun = 0;
        String cat;
        if (nextGeneral) cat = "general";
        else cat = ChatStore.classify(plain, cfg);
        nextGeneral = false;
        store.add(message, cat, cfg.historyPerCategory);
        if (window != null) window.onNew();
    }
}
