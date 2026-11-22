package kr.healingtown.chat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.message.ChatVisibility;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 채팅창 그리기 · 마우스
 *  채팅을 닫았을 때: 새 채팅만 잠깐 (표시 시간) - '항상 표시' 면 창 그대로
 *  채팅을 열었을 때: 위 탭 · 자물쇠 (위치 잠금) · 톱니바퀴 (설정) · 오른쪽 아래 크기 조절 · 위 탭 줄을 끌어 옮기기 · 휠 스크롤
 */
public final class ChatWindow {

    // ------------------------------------------------------------------ 색 (이미지처럼: 어두운 반투명 + 청록)
    static final int BG = 0x0B0F14, BORDER = 0x3A4A5A, TEAL = 0xFF4FD1BE, TEXT = 0xFFE8EEF2, GRAY = 0xFF9AA6B2, DIM = 0xFF5C6773;
    static final int PANEL_BG = 0xF2141920, PANEL_LINE = 0xFF2E3946, OFF = 0xFF3A4450;
    /** 탭 이름 색 (안 고른 탭도 잘 보이게 밝게) · 고른 탭 · 마우스 올림 */
    static final int TAB_TEXT = 0xFFDCE6EE, TAB_SEL = 0xFF5CF5DF, TAB_HOVER = 0xFFFFE08A;
    static final int HEAD = 14, PAD = 4;

    final MinecraftClient mc = MinecraftClient.getInstance();

    /** 맨 아래에서 몇 줄 올렸는지 */
    int scroll;
    boolean settingsOpen;
    final Map<String, Long> seen = new HashMap<>();

    // 이번 화면에 그린 것 (마우스 판정)
    record Row(int x, int y, float s, OrderedText text) {}
    record Hit(String id, int x0, int y0, int x1, int y1) {}
    final List<Row> rows = new ArrayList<>();
    final List<Hit> hits = new ArrayList<>();

    // 끌기: 0 없음 · 1 옮기기 · 2 크기 · 3 슬라이더
    int drag;
    double offX, offY;
    String dragSlider;
    boolean dirty;

    // 창 위치 (이번 화면)
    int x, y, w, h, sw, sh;

    static final int EDGE = 3;
    // 마우스 모양 (0 기본 · 1 좌우 · 2 위아래)
    int cursorNow;
    long curH, curV;

    void setCursor(int k) {
        if (k == cursorNow) return;
        try {
            long win = mc.getWindow().getHandle();
            if (k == 0) GLFW.glfwSetCursor(win, 0L);
            else {
                if (curH == 0) curH = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR);
                if (curV == 0) curV = GLFW.glfwCreateStandardCursor(GLFW.GLFW_VRESIZE_CURSOR);
                GLFW.glfwSetCursor(win, k == 1 ? curH : curV);
            }
            cursorNow = k;
        } catch (Throwable ignored) {}
    }

    // 탭 줄 넘기기 (창이 좁을 때)
    static final int GAP = 9, ARROW_W = 9;
    int tabOff, tabMaxOff;
    boolean tabOverflow;

    // ================================================================== 위치
    void layout() {
        ChatConfig c = HtChat.cfg;
        sw = mc.getWindow().getScaledWidth();
        sh = mc.getWindow().getScaledHeight();
        w = Math.max(Math.min(ChatConfig.MIN_W, sw - 4), Math.min(c.width, sw - 4));
        h = Math.max(Math.min(ChatConfig.MIN_H, sh - 4), Math.min(c.height, sh - 4));
        x = clamp(c.left, 0, Math.max(0, sw - w));
        y = clamp(sh - c.bottom - h, 0, Math.max(0, sh - h));
    }

    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    static int argb(float a, int rgb) { return (Math.round(Math.max(0, Math.min(1, a)) * 255) << 24) | (rgb & 0xFFFFFF); }

    int lineH() { return Math.max(5, Math.round(9 * HtChat.cfg.textScale)); }

    int wrapWidth() { return Math.max(40, (int) ((w - PAD * 2 - 4) / HtChat.cfg.textScale)); }

    List<OrderedText> wrapped(ChatStore.Line l) {
        ChatConfig c = HtChat.cfg;
        int ww = wrapWidth();
        int key = ww * 2 + (c.showTime ? 1 : 0);
        if (l.wrapKey != key || l.wrapped == null) {
            Text t = c.showTime ? Text.literal(l.clock + " ").formatted(Formatting.GRAY).append(l.text) : l.text;
            l.wrapped = mc.textRenderer.wrapLines(t, ww);
            l.wrapKey = key;
        }
        return l.wrapped;
    }

    boolean chatHidden() {
        return mc.options.hudHidden || mc.options.getChatVisibility().getValue() == ChatVisibility.HIDDEN;
    }

    // ================================================================== 새 채팅
    void onNew() {
        if (scroll > 0) scroll++;        // 위로 올려 보던 중이면 보던 곳 그대로
    }

    // ================================================================== 채팅을 닫았을 때 (HUD)
    void renderHud(DrawContext ctx) {
        if (!HtChat.active() || chatHidden() || mc.currentScreen instanceof ChatScreen) return;
        layout();
        if (HtChat.cfg.alwaysShow) { drawWindow(ctx, false, -1, -1); return; }
        drawFading(ctx);
    }

    /** 새 채팅만 잠깐 (표시 시간, 마지막 1초 동안 흐려짐) */
    void drawFading(DrawContext ctx) {
        ChatConfig c = HtChat.cfg;
        long now = System.currentTimeMillis();
        long life = c.fadeSeconds * 1000L;
        List<ChatStore.Line> view = HtChat.store.view(c.tab, c);
        int lh = lineH();
        int bottomY = y + h - 3;
        int top = y + HEAD + 2;
        int rowY = bottomY;
        TextRenderer tr = mc.textRenderer;
        for (int i = view.size() - 1; i >= 0 && rowY - lh >= top; i--) {
            ChatStore.Line l = view.get(i);
            long age = now - l.time;
            if (age > life) break;
            float a = Math.min(1f, (life - age) / 1000f);
            List<OrderedText> ws = wrapped(l);
            for (int k = ws.size() - 1; k >= 0 && rowY - lh >= top; k--) {
                rowY -= lh;
                ctx.fill(x, rowY, x + w, rowY + lh, argb(c.opacity * a, BG));
                int alpha = Math.round(a * 255);
                if (alpha > 8) drawLine(ctx, tr, ws.get(k), x + PAD, rowY, (alpha << 24) | 0xFFFFFF, null);
            }
        }
    }

    void drawLine(DrawContext ctx, TextRenderer tr, OrderedText t, int lx, int ly, int color, List<Row> hit) {
        float s = HtChat.cfg.textScale;
        int ty = ly + Math.max(0, (lineH() - Math.round(8 * s)) / 2);
        if (Math.abs(s - 1f) < 0.001f) {
            ctx.drawText(tr, t, lx, ty, color, true);
        } else {
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate((float) lx, (float) ty, 0f);
            m.scale(s, s, 1f);
            ctx.drawText(tr, t, 0, 0, color, true);
            m.pop();
        }
        if (hit != null) hit.add(new Row(lx, ly, s, t));
    }

    // ================================================================== 창 (열었을 때 · 항상 표시)
    void drawWindow(DrawContext ctx, boolean focused, int mx, int my) {
        ChatConfig c = HtChat.cfg;
        TextRenderer tr = mc.textRenderer;
        rows.clear();
        hits.clear();
        ctx.fill(x, y, x + w, y + h, argb(c.opacity, BG));
        ctx.drawBorder(x, y, w, h, argb(Math.min(1f, c.opacity + 0.25f), BORDER));

        // ---- 탭 (창이 좁아서 다 안 들어가면 양 끝 ◀ ▶ · 탭 줄에서 휠로 넘김)
        int right = x + w - (focused ? 30 : 4);
        float ts = 0.85f;
        long now = System.currentTimeMillis();
        if (focused) {
            if (Cat.ALL.equals(c.tab)) for (String id : Cat.IDS) { if (!Boolean.FALSE.equals(c.inAll.get(id))) seen.put(id, now); }
            else seen.put(c.tab, now);
        }
        String[] tabs = Cat.TABS;
        int n = tabs.length;
        int[] tws = new int[n];
        int totalW = -GAP;
        for (int i = 0; i < n; i++) { tws[i] = Math.round(tr.getWidth(Cat.name(tabs[i])) * ts); totalW += tws[i] + GAP; }
        int tx = x + 5, tRight = right;
        tabOverflow = tx + totalW > right;
        if (tabOverflow) {
            tx = x + 5 + ARROW_W;                 // 왼쪽 ◀ 자리
            tRight = right - ARROW_W;             // 오른쪽 ▶ 자리
            int avail = tRight - tx;
            int maxOff = n - 1, acc = -GAP;        // 끝까지 넘겼을 때 첫 탭 (뒤에서부터 들어가는 만큼)
            for (int i = n - 1; i >= 0; i--) { acc += tws[i] + GAP; if (acc > avail) break; maxOff = i; }
            tabMaxOff = maxOff;
            tabOff = clamp(tabOff, 0, maxOff);
        } else {
            tabOff = 0;
            tabMaxOff = 0;
        }
        ctx.fill(x + 1, y + 1, x + w - 1, y + HEAD, argb(Math.max(0.55f, Math.min(1f, c.opacity + 0.25f)), 0x0B0F14));   // 탭 줄 어두운 띠
        int lastShown = -1;
        for (int i = tabOverflow ? tabOff : 0; i < n; i++) {
            String id = tabs[i];
            String label = Cat.name(id);
            int tw = tws[i];
            if (tx + tw > tRight) break;
            lastShown = i;
            boolean sel = id.equals(c.tab);
            boolean hover = focused && mx >= tx - 3 && mx < tx + tw + 3 && my >= y && my < y + HEAD;
            int col = sel ? TAB_SEL : hover ? TAB_HOVER : TAB_TEXT;
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate((float) tx, (float) (y + 4), 0f);
            m.scale(ts, ts, 1f);
            ctx.drawText(tr, label, 0, 0, col, true);                     // 그림자 → 밝은 바탕 위에서도 잘 보이게
            m.pop();
            if (sel) ctx.fill(tx - 1, y + HEAD - 2, tx + tw + 1, y + HEAD - 1, TEAL);
            if (!Cat.ALL.equals(id) && !sel && HtChat.store.lastTime(id) > seen.getOrDefault(id, 0L))
                ctx.fill(tx + tw, y + 3, tx + tw + 2, y + 5, 0xFFFFC857);                   // 안 읽은 채팅
            hits.add(new Hit("tab:" + id, tx - 3, y, tx + tw + 3, y + HEAD));
            tx += tw + GAP;
        }
        if (tabOverflow) {
            boolean canL = tabOff > 0, canR = lastShown < n - 1;
            int cy = y + HEAD / 2 - 1;
            int lx = x + 3, rx = right - 6;
            boolean hl = focused && canL && mx >= x + 1 && mx < x + 5 + ARROW_W - 2 && my >= y && my < y + HEAD;
            boolean hr = focused && canR && mx >= right - ARROW_W && mx < right + 1 && my >= y && my < y + HEAD;
            drawArrow(ctx, lx, cy, false, !canL ? DIM : hl ? TEXT : TEAL);
            drawArrow(ctx, rx, cy, true, !canR ? DIM : hr ? TEXT : TEAL);
            if (focused) {
                hits.add(0, new Hit("tabprev", x + 1, y, x + 5 + ARROW_W - 2, y + HEAD));
                hits.add(0, new Hit("tabnext", right - ARROW_W, y, right + 1, y + HEAD));
            }
        }
        ctx.fill(x + 1, y + HEAD, x + w - 1, y + HEAD + 1, argb(Math.min(1f, c.opacity + 0.2f), BORDER));

        // ---- 자물쇠 · 톱니바퀴
        if (focused) {
            int gx = x + w - 13, lx = x + w - 26;
            drawLock(ctx, lx, y + 3, c.locked, mx >= lx - 1 && mx < lx + 10 && my >= y && my < y + HEAD);
            hits.add(new Hit("lock", lx - 2, y, lx + 11, y + HEAD));
            boolean gh = settingsOpen || (mx >= gx - 1 && mx < gx + 10 && my >= y && my < y + HEAD);
            ctx.drawText(tr, "⚙", gx, y + 3, gh ? TEAL : GRAY, false);
            hits.add(new Hit("gear", gx - 2, y, gx + 11, y + HEAD));
        }

        // ---- 채팅 줄
        int top = y + HEAD + 2, bottomY = y + h - 3;
        int lh = lineH();
        List<ChatStore.Line> view = HtChat.store.view(c.tab, c);
        int visible = Math.max(1, (bottomY - top) / lh);
        int total = 0;
        for (ChatStore.Line l : view) total += wrapped(l).size();
        int maxScroll = Math.max(0, total - visible);
        scroll = clamp(scroll, 0, maxScroll);
        ctx.enableScissor(x + 1, top, x + w - 1, bottomY);
        int skip = scroll;
        int rowY = bottomY;
        outer:
        for (int i = view.size() - 1; i >= 0; i--) {
            List<OrderedText> ws = wrapped(view.get(i));
            for (int k = ws.size() - 1; k >= 0; k--) {
                if (skip > 0) { skip--; continue; }
                rowY -= lh;
                if (rowY < top - lh) break outer;
                drawLine(ctx, tr, ws.get(k), x + PAD, rowY, 0xFFFFFFFF, focused ? rows : null);
            }
        }
        ctx.disableScissor();
        if (view.isEmpty() && focused) ctx.drawText(tr, "아직 채팅이 없습니다", x + PAD, bottomY - 10, DIM, false);

        // ---- 스크롤 막대
        if (focused && maxScroll > 0) {
            int areaH = bottomY - top;
            int barH = Math.max(8, areaH * visible / total);
            int barY = bottomY - barH - (int) ((long) (areaH - barH) * scroll / maxScroll);
            ctx.fill(x + w - 3, top, x + w - 2, bottomY, 0x30FFFFFF);
            ctx.fill(x + w - 3, barY, x + w - 2, barY + barH, TEAL);
        }

        // ---- 크기 조절 (오른쪽 아래)
        if (focused && !c.locked) {
            int gx = x + w - 2, gy = y + h - 2;                              // 오른쪽 아래 끝
            boolean gh = mx >= x + w - 11 && mx < x + w && my >= y + h - 11 && my < y + h;
            int col = drag == 2 ? TEAL : gh ? TAB_HOVER : 0xE6FFFFFF;
            drawResizeArrow(ctx, gx - 8, gy - 8, col);
            hits.add(0, new Hit("grip", x + w - 11, y + h - 11, x + w, y + h));
            // 테두리 끌어서 크기: 오른쪽 · 왼쪽 = 폭, 아래 = 높이 (위 탭 줄은 옮기기)
            hits.add(1, new Hit("edgeR", x + w - EDGE, y + HEAD + 1, x + w, y + h - 9));
            hits.add(2, new Hit("edgeL", x, y + HEAD + 1, x + EDGE, y + h));
            hits.add(3, new Hit("edgeB", x + EDGE, y + h - EDGE, x + w - 9, y + h));
            String he = drag == 4 ? "edgeR" : drag == 5 ? "edgeL" : drag == 6 ? "edgeB" : null;
            if (he == null) { Hit hh = hitAt(mx, my); if (hh != null && hh.id().startsWith("edge")) he = hh.id(); }
            if ("edgeR".equals(he)) ctx.fill(x + w - 1, y + HEAD + 1, x + w, y + h, TEAL);
            if ("edgeL".equals(he)) ctx.fill(x, y + HEAD + 1, x + 1, y + h, TEAL);
            if ("edgeB".equals(he)) ctx.fill(x, y + h - 1, x + w, y + h, TEAL);
            setCursor(he == null ? 0 : he.equals("edgeB") ? 2 : 1);
        } else if (focused) setCursor(0);
        if (focused) hits.add(new Hit("head", x, y, x + w, y + HEAD));
    }

    /** 작은 삼각형 화살표 (폭 4, 높이 7) - right = ▶ */
    /** 크기 조절 화살표 ⤡ (왼쪽 위 ↔ 오른쪽 아래, 9x9, 그림자) */
    static void drawResizeArrow(DrawContext ctx, int x0, int y0, int col) {
        for (int pass = 0; pass < 2; pass++) {
            int o = pass == 0 ? 1 : 0, c = pass == 0 ? 0xC0000000 : col;
            for (int i = 1; i < 8; i++) ctx.fill(x0 + i + o, y0 + i + o, x0 + i + 1 + o, y0 + i + 1 + o, c);       // 대각선
            for (int i = 0; i < 4; i++) {
                ctx.fill(x0 + i + o, y0 + o, x0 + i + 1 + o, y0 + 1 + o, c);                                // ↖ 머리
                ctx.fill(x0 + o, y0 + i + o, x0 + 1 + o, y0 + i + 1 + o, c);
                ctx.fill(x0 + 8 - i + o, y0 + 8 + o, x0 + 9 - i + o, y0 + 9 + o, c);                        // ↘ 머리
                ctx.fill(x0 + 8 + o, y0 + 8 - i + o, x0 + 9 + o, y0 + 9 - i + o, c);
            }
        }
    }

    static void drawArrow(DrawContext ctx, int ax, int cy, boolean right, int col) {
        for (int i = 0; i < 4; i++) {
            int xx = right ? ax + i : ax + 3 - i;
            ctx.fill(xx, cy - (3 - i), xx + 1, cy + (3 - i) + 1, col);
        }
    }

    /** 탭을 한 칸 넘김 (d = -1 왼쪽, 1 오른쪽) */
    void shiftTabs(int d) {
        int nv = clamp(tabOff + d, 0, tabMaxOff);
        if (nv != tabOff) { tabOff = nv; click(); }
    }

    static void drawLock(DrawContext ctx, int lx, int ly, boolean locked, boolean hover) {
        int col = locked ? TEAL : hover ? TEXT : GRAY;
        // 고리
        ctx.fill(lx + 2, ly, lx + 6, ly + 1, col);
        ctx.fill(lx + 1, ly + 1, lx + 2, ly + 4, col);
        if (locked) ctx.fill(lx + 6, ly + 1, lx + 7, ly + 4, col);
        else ctx.fill(lx + 6, ly, lx + 7, ly + 1, col);
        // 몸통
        ctx.fill(lx, ly + 4, lx + 8, ly + 9, col);
        ctx.fill(lx + 3, ly + 5, lx + 5, ly + 7, 0xFF0B0F14);
    }

    // ================================================================== 채팅을 열었을 때 (화면 위에)
    void renderFocused(DrawContext ctx, int mx, int my) {
        if (!HtChat.active()) return;
        layout();
        updateDrag(mx, my);
        layout();
        drawWindow(ctx, true, mx, my);
        if (settingsOpen) Settings.draw(this, ctx, mx, my);
        // 마우스 올린 글자: 아이템 · 글자 설명
        if (!(settingsOpen && Settings.inside(this, mx, my))) {
            Style st = styleAt(mx, my);
            if (st != null && st.getHoverEvent() != null) ctx.drawHoverEvent(mc.textRenderer, st, mx, my);
        }
    }

    Style styleAt(double mx, double my) {
        for (Row r : rows) {
            if (my >= r.y() && my < r.y() + lineH() && mx >= r.x()) {
                int tx = (int) ((mx - r.x()) / r.s());
                return mc.textRenderer.getTextHandler().getStyleAt(r.text(), tx);
            }
        }
        return null;
    }

    Hit hitAt(double mx, double my) {
        for (Hit hh : hits) if (mx >= hh.x0() && mx < hh.x1() && my >= hh.y0() && my < hh.y1()) return hh;
        return null;
    }

    boolean insideWindow(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ================================================================== 마우스
    /** 눌렀을 때 - 이 창 · 설정 안이면 true (바닐라 채팅 화면에는 안 넘김) */
    boolean mouseDown(Screen screen, double mx, double my, int button) {
        if (!HtChat.active()) return false;
        ChatConfig c = HtChat.cfg;
        if (settingsOpen && Settings.inside(this, mx, my)) {
            if (button == 0) Settings.click(this, mx, my);
            return true;
        }
        if (!insideWindow(mx, my)) return false;
        Hit hh = hitAt(mx, my);
        if (button == 0 && hh != null) {
            String id = hh.id();
            if (id.startsWith("tab:")) {
                c.tab = id.substring(4);
                scroll = 0;
                dirty = true;
                click();
                return true;
            }
            switch (id) {
                case "tabprev" -> { shiftTabs(-1); return true; }
                case "tabnext" -> { shiftTabs(1); return true; }
                case "gear" -> { settingsOpen = !settingsOpen; click(); return true; }
                case "lock" -> { c.locked = !c.locked; dirty = true; click(); return true; }
                case "grip" -> { if (!c.locked) { drag = 2; return true; } }
                case "edgeR" -> { if (!c.locked) { drag = 4; return true; } }
                case "edgeL" -> { if (!c.locked) { drag = 5; return true; } }
                case "edgeB" -> { if (!c.locked) { drag = 6; return true; } }
                case "head" -> {
                    if (!c.locked) { drag = 1; offX = mx - x; offY = my - y; }
                    return true;
                }
                default -> {}
            }
        }
        Style st = styleAt(mx, my);
        if (button == 0 && st != null && screen != null) screen.handleTextClick(st);
        return true;
    }

    void mouseUp() {
        drag = 0;
        dragSlider = null;
        if (dirty) { HtChat.cfg.save(); dirty = false; }
    }

    boolean scroll(double mx, double my, double amount) {
        if (!HtChat.active()) return false;
        if (settingsOpen && Settings.inside(this, mx, my)) return true;
        if (!insideWindow(mx, my)) return false;
        if (tabOverflow && my < y + HEAD) {                        // 탭 줄 위에서 휠 = 탭 넘기기
            if (amount != 0) shiftTabs(amount > 0 ? -1 : 1);
            return true;
        }
        int step = Screen.hasShiftDown() ? 7 : 2;
        scroll = Math.max(0, scroll + (amount > 0 ? step : amount < 0 ? -step : 0));
        return true;
    }

    /** 끄는 중 (버튼을 떼면 끝) - 화면을 그릴 때마다 */
    void updateDrag(int mx, int my) {
        if (drag == 0) return;
        if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) { mouseUp(); return; }
        ChatConfig c = HtChat.cfg;
        if (drag == 1) {
            int nx = clamp((int) Math.round(mx - offX), 0, Math.max(0, sw - w));
            int ny = clamp((int) Math.round(my - offY), 0, Math.max(0, sh - h));
            c.left = nx;
            c.bottom = sh - (ny + h);
            dirty = true;
        } else if (drag == 2) {
            int nw = clamp(mx - x, ChatConfig.MIN_W, sw - x);
            int bottomEdge = clamp(my, y + ChatConfig.MIN_H, sh);
            c.width = nw;
            c.height = bottomEdge - y;
            c.bottom = sh - bottomEdge;
            dirty = true;
        } else if (drag == 4) {                                    // 오른쪽 테두리: 폭
            c.width = clamp(mx - x, ChatConfig.MIN_W, sw - x);
            dirty = true;
        } else if (drag == 5) {                                    // 왼쪽 테두리: 오른쪽 끝은 그대로
            int rightEdge = x + w;
            int nx = clamp(mx, 0, rightEdge - ChatConfig.MIN_W);
            c.left = nx;
            c.width = rightEdge - nx;
            dirty = true;
        } else if (drag == 6) {                                    // 아래 테두리: 높이
            int bottomEdge = clamp(my, y + ChatConfig.MIN_H, sh);
            c.height = bottomEdge - y;
            c.bottom = sh - bottomEdge;
            dirty = true;
        } else if (drag == 3 && dragSlider != null) {
            Settings.slide(this, dragSlider, mx);
            dirty = true;
        }
    }

    void closed() {
        setCursor(0);
        drag = 0;
        dragSlider = null;
        scroll = 0;
        if (dirty) { HtChat.cfg.save(); dirty = false; }
    }

    void click() {
        try { mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f)); } catch (Throwable ignored) {}
    }
}
