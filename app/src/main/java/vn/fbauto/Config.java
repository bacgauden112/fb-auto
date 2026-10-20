package vn.fbauto;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Config {
    public static final String PREFS = "fbauto";

    public String recipe;
    public int minDelay, maxDelay, maxCount, pauseEvery, pauseMinutes;
    public long stepTimeoutMs = 5000, optionalTimeoutMs = 2500;

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Config load(Context ctx) {
        SharedPreferences p = prefs(ctx);
        Config c = new Config();
        c.recipe = p.getString("recipe", Recipe.PRESET_UNFOLLOW_PAGES);
        c.minDelay = Math.max(1, p.getInt("minDelay", 8));
        c.maxDelay = Math.max(c.minDelay, p.getInt("maxDelay", 20));
        c.maxCount = Math.max(1, p.getInt("maxCount", 50));
        c.pauseEvery = Math.max(0, p.getInt("pauseEvery", 15));
        c.pauseMinutes = Math.max(0, p.getInt("pauseMinutes", 10));
        return c;
    }

    // ---------- danh sách nhóm đã quét / đã chọn ----------

    /** Mỗi phần tử: {tên hiển thị, dòng phụ}. */
    public static List<String[]> loadScanned(Context ctx) {
        List<String[]> out = new ArrayList<>();
        String raw = prefs(ctx).getString("scanned", "");
        for (String line : raw.split("\n")) {
            if (line.trim().isEmpty()) continue;
            String[] p = line.split("\t", 2);
            out.add(new String[]{p[0], p.length > 1 ? p[1] : ""});
        }
        return out;
    }

    public static void saveScanned(Context ctx, List<String[]> items) {
        StringBuilder sb = new StringBuilder();
        for (String[] it : items) sb.append(it[0]).append('\t').append(it[1]).append('\n');
        prefs(ctx).edit().putString("scanned", sb.toString()).apply();
    }

    /** Tên nhóm đã chọn (đã chuẩn hóa bằng Recipe.norm). */
    public static Set<String> loadSelected(Context ctx) {
        Set<String> out = new LinkedHashSet<>();
        for (String line : prefs(ctx).getString("selected", "").split("\n")) {
            if (!line.trim().isEmpty()) out.add(line.trim());
        }
        return out;
    }

    public static void saveSelected(Context ctx, Set<String> names) {
        prefs(ctx).edit().putString("selected", String.join("\n", names)).apply();
    }

    /** Xóa 1 nhóm (đã rời) khỏi danh sách quét và danh sách chọn. */
    public static synchronized void removeGroup(Context ctx, String normName) {
        List<String[]> items = loadScanned(ctx);
        List<String[]> keep = new ArrayList<>();
        for (String[] it : items) if (!Recipe.norm(it[0]).equals(normName)) keep.add(it);
        if (keep.size() != items.size()) saveScanned(ctx, keep);
        Set<String> sel = loadSelected(ctx);
        if (sel.remove(normName)) saveSelected(ctx, sel);
    }
}
