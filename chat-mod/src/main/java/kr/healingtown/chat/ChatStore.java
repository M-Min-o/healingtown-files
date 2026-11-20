package kr.healingtown.chat;

import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 받은 채팅 (종류 · 시간) - 오래된 것부터 */
public final class ChatStore {

    static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    public static final class Line {
        final Text text;
        final String cat;
        final long time;
        final String clock;
        List<OrderedText> wrapped;
        int wrapKey = Integer.MIN_VALUE;

        Line(Text text, String cat) {
            this.text = text;
            this.cat = cat;
            this.time = System.currentTimeMillis();
            this.clock = LocalTime.now().format(HM);
        }
    }

    private final List<Line> lines = new ArrayList<>();
    private final Map<String, Integer> counts = new HashMap<>();
    /** 바뀔 때마다 +1 (창이 스크롤 위치를 맞출 때 씀) */
    int version;

    public synchronized void add(Text text, String cat, int cap) {
        lines.add(new Line(text, cat));
        int n = counts.merge(cat, 1, Integer::sum);
        if (n > cap) {
            for (Iterator<Line> it = lines.iterator(); it.hasNext(); ) {
                if (it.next().cat.equals(cat)) { it.remove(); counts.merge(cat, -1, Integer::sum); break; }
            }
        }
        version++;
    }

    public synchronized void clear() {
        lines.clear();
        counts.clear();
        version++;
    }

    /** 이 탭에 보일 채팅 (오래된 것부터) */
    public synchronized List<Line> view(String tab, ChatConfig cfg) {
        List<Line> out = new ArrayList<>();
        for (Line l : lines) {
            if (Cat.ALL.equals(tab) ? !Boolean.FALSE.equals(cfg.inAll.get(l.cat)) : l.cat.equals(tab)) out.add(l);
        }
        return out;
    }

    /** 새 채팅이 이 탭에 안 보이는 다른 탭에 들어왔는지 (탭에 점 표시) */
    public synchronized long lastTime(String cat) {
        for (int i = lines.size() - 1; i >= 0; i--) if (lines.get(i).cat.equals(cat)) return lines.get(i).time;
        return 0;
    }

    /** 색 코드 뺀 글자 → 종류 */
    static String classify(String plain, ChatConfig cfg) {
        for (Map.Entry<String, List<Pattern>> en : cfg.patterns().entrySet())
            for (Pattern p : en.getValue()) if (p.matcher(plain).find()) return en.getKey();
        return "system";
    }
}
