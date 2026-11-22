package kr.healingtown.chat;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 채팅 설정 창 (톱니바퀴) - 화면 오른쪽
 *  [전체 탭에 보일 채팅] 종류마다 켜기 · 끄기
 *  [전체 설정] 배경 불투명도 · 글자 크기 · 표시 시간 · 종류별 기록 개수 · 항상 표시 · 시간 표시 · 위치 잠금 · 처음대로
 */
final class Settings {
    private Settings() {}

    static final int PW = 176, RH = 14, TOP = 18;

    /** 한 줄: kind = label · toggle · slider · button · line */
    record Item(String kind, String key, String label) {}

    static List<Item> items() {
        List<Item> l = new ArrayList<>();
        l.add(new Item("label", null, "전체 탭에 보일 채팅"));
        for (String id : Cat.IDS) if (Cat.isTab(id)) l.add(new Item("toggle", "in:" + id, Cat.name(id)));
        l.add(new Item("line", null, ""));
        l.add(new Item("label", null, "전체 설정"));
        l.add(new Item("slider", "opacity", "배경 불투명도"));
        l.add(new Item("slider", "scale", "글자 크기"));
        l.add(new Item("slider", "fade", "표시 시간"));
        l.add(new Item("slider", "history", "종류별 기록"));
        l.add(new Item("toggle", "always", "항상 표시"));
        l.add(new Item("toggle", "time", "시간 표시"));
        l.add(new Item("toggle", "lock", "위치 잠금"));
        l.add(new Item("button", "reset", "위치 · 크기 처음대로"));
        return l;
    }

    static int panelH() { return TOP + items().size() * RH + 6; }

    static float scale(ChatWindow win) {
        return Math.min(1f, (win.sh - 12) / (float) panelH());
    }

    static int px(ChatWindow win) { return win.sw - Math.round(PW * scale(win)) - 6; }

    static int py(ChatWindow win) { return Math.max(6, (win.sh - Math.round(panelH() * scale(win))) / 2); }

    static boolean inside(ChatWindow win, double mx, double my) {
        float s = scale(win);
        return mx >= px(win) && mx < px(win) + PW * s && my >= py(win) && my < py(win) + panelH() * s;
    }

    // ------------------------------------------------------------------ 값
    static boolean on(String key) {
        ChatConfig c = HtChat.cfg;
        if (key.startsWith("in:")) return !Boolean.FALSE.equals(c.inAll.get(key.substring(3)));
        return switch (key) {
            case "always" -> c.alwaysShow;
            case "time" -> c.showTime;
            case "lock" -> c.locked;
            default -> false;
        };
    }

    static void flip(String key) {
        ChatConfig c = HtChat.cfg;
        if (key.startsWith("in:")) { c.inAll.put(key.substring(3), !on(key)); return; }
        switch (key) {
            case "always" -> c.alwaysShow = !c.alwaysShow;
            case "time" -> c.showTime = !c.showTime;
            case "lock" -> c.locked = !c.locked;
            default -> {}
        }
    }

    /** 0 ~ 1 */
    static float frac(String key) {
        ChatConfig c = HtChat.cfg;
        return switch (key) {
            case "opacity" -> c.opacity;
            case "scale" -> (c.textScale - 0.5f) / 1.5f;
            case "fade" -> (c.fadeSeconds - 1) / 59f;
            case "history" -> (c.historyPerCategory - 100) / 900f;
            default -> 0;
        };
    }

    static void setFrac(String key, float f) {
        ChatConfig c = HtChat.cfg;
        f = Math.max(0, Math.min(1, f));
        switch (key) {
            case "opacity" -> c.opacity = Math.round(f * 100) / 100f;
            case "scale" -> c.textScale = Math.round((0.5f + f * 1.5f) * 20) / 20f;          // 5% 단위
            case "fade" -> c.fadeSeconds = 1 + Math.round(f * 59);
            case "history" -> c.historyPerCategory = 100 + Math.round(f * 18) * 50;          // 50 개 단위
            default -> {}
        }
    }

    static String valueText(String key) {
        ChatConfig c = HtChat.cfg;
        return switch (key) {
            case "opacity" -> Math.round(c.opacity * 100) + "%";
            case "scale" -> Math.round(c.textScale * 100) + "%";
            case "fade" -> c.fadeSeconds + "초";
            case "history" -> c.historyPerCategory + "개";
            default -> "";
        };
    }

    // ------------------------------------------------------------------ 그리기
    static final int SL_X0 = 74, SL_X1 = PW - 40;

    static void draw(ChatWindow win, DrawContext ctx, int mx, int my) {
        TextRenderer tr = win.mc.textRenderer;
        float s = scale(win);
        int px = px(win), py = py(win);
        double lx = (mx - px) / s, ly = (my - py) / s;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate((float) px, (float) py, 200f);
        m.scale(s, s, 1f);
        int ph = panelH();
        ctx.fill(0, 0, PW, ph, ChatWindow.PANEL_BG);
        ctx.drawBorder(0, 0, PW, ph, ChatWindow.PANEL_LINE);
        ctx.drawText(tr, "채팅 설정", 7, 6, ChatWindow.TEXT, false);
        boolean xh = lx >= PW - 14 && lx < PW - 2 && ly >= 2 && ly < 15;
        ctx.drawText(tr, "✕", PW - 12, 5, xh ? ChatWindow.TEAL : ChatWindow.GRAY, false);
        List<Item> items = items();
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            int ry = TOP + i * RH;
            boolean hover = lx >= 2 && lx < PW - 2 && ly >= ry && ly < ry + RH;
            switch (it.kind()) {
                case "label" -> ctx.drawText(tr, it.label(), 7, ry + 4, ChatWindow.TEAL, false);
                case "line" -> ctx.fill(6, ry + RH / 2, PW - 6, ry + RH / 2 + 1, ChatWindow.PANEL_LINE);
                case "toggle" -> {
                    if (hover) ctx.fill(3, ry, PW - 3, ry + RH, 0x18FFFFFF);
                    ctx.drawText(tr, it.label(), 10, ry + 3, ChatWindow.TEXT, false);
                    drawSwitch(ctx, PW - 30, ry + 2, on(it.key()));
                }
                case "slider" -> {
                    ctx.drawText(tr, it.label(), 7, ry + 3, ChatWindow.GRAY, false);
                    int tx0 = SL_X0, tx1 = SL_X1, ty = ry + RH / 2;
                    float f = frac(it.key());
                    int kx = tx0 + Math.round((tx1 - tx0) * f);
                    ctx.fill(tx0, ty - 1, tx1, ty + 1, 0xFF3A4450);
                    ctx.fill(tx0, ty - 1, kx, ty + 1, ChatWindow.TEAL);
                    boolean active = it.key().equals(win.dragSlider);
                    ctx.fill(kx - 2, ty - 3, kx + 2, ty + 3, active || (hover && lx >= tx0 - 4 && lx <= tx1 + 4) ? 0xFFFFFFFF : 0xFFDDE5EA);
                    ctx.drawText(tr, valueText(it.key()), SL_X1 + 6, ry + 3, ChatWindow.TEXT, false);
                }
                case "button" -> {
                    int bx0 = 7, bx1 = PW - 7;
                    ctx.fill(bx0, ry + 1, bx1, ry + RH - 1, hover ? 0xFF2C3A47 : 0xFF222B35);
                    ctx.drawBorder(bx0, ry + 1, bx1 - bx0, RH - 2, ChatWindow.PANEL_LINE);
                    int tw = tr.getWidth(it.label());
                    ctx.drawText(tr, it.label(), (bx0 + bx1 - tw) / 2, ry + 3, hover ? ChatWindow.TEAL : ChatWindow.TEXT, false);
                }
                default -> {}
            }
        }
        m.pop();
    }

    static void drawSwitch(DrawContext ctx, int sx, int sy, boolean on) {
        int w = 20, h = 10;
        ctx.fill(sx + 1, sy, sx + w - 1, sy + h, on ? 0xFF2E8F82 : ChatWindow.OFF);
        ctx.fill(sx, sy + 1, sx + w, sy + h - 1, on ? 0xFF2E8F82 : ChatWindow.OFF);
        int kx = on ? sx + w - 9 : sx + 1;
        ctx.fill(kx, sy + 1, kx + 8, sy + h - 1, on ? 0xFFEFFFFC : 0xFFB8C2CC);
    }

    // ------------------------------------------------------------------ 마우스
    static void click(ChatWindow win, double mx, double my) {
        float s = scale(win);
        double lx = (mx - px(win)) / s, ly = (my - py(win)) / s;
        if (lx >= PW - 14 && ly < 15) { win.settingsOpen = false; win.click(); return; }
        List<Item> items = items();
        int i = (int) Math.floor((ly - TOP) / RH);
        if (i < 0 || i >= items.size()) return;
        Item it = items.get(i);
        switch (it.kind()) {
            case "toggle" -> { flip(it.key()); win.dirty = true; win.click(); }
            case "slider" -> {
                if (lx < SL_X0 - 6 || lx > SL_X1 + 6) return;
                win.drag = 3;
                win.dragSlider = it.key();
                slide(win, it.key(), mx);
                win.dirty = true;
            }
            case "button" -> {
                if ("reset".equals(it.key())) {
                    ChatConfig d = new ChatConfig();
                    ChatConfig c = HtChat.cfg;
                    c.left = d.left; c.bottom = d.bottom; c.width = d.width; c.height = d.height;
                    win.dirty = true;
                    win.click();
                }
            }
            default -> {}
        }
    }

    static void slide(ChatWindow win, String key, double mx) {
        float s = scale(win);
        double lx = (mx - px(win)) / s;
        setFrac(key, (float) ((lx - SL_X0) / (SL_X1 - SL_X0)));
    }
}
