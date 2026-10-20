package vn.fbauto;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Kịch bản dạng văn bản, mỗi dòng một bước:
 *   * A | B   bước đầu: tìm mục trong danh sách có nút tên A hoặc B
 *   A | B     bấm nút A hoặc B (bắt buộc)
 *   ? A | B   bấm nếu có (không bắt buộc)
 *   ~A        khớp khi chữ CHỨA "A" (thay vì phải giống hệt)
 *   BACK      bấm nút quay lại
 *   WAIT 2    chờ 2 giây
 *   # ...     ghi chú
 */
public class Recipe {
    public static final int TARGET = 0, CLICK = 1, BACK = 2, WAIT = 3;

    public static class Step {
        public int type;
        public boolean optional;
        public long waitMs;
        public final List<String> exact = new ArrayList<>();
        public final List<String> contains = new ArrayList<>();

        public boolean matches(String t) {
            if (t == null || t.isEmpty()) return false;
            if (exact.contains(t)) return true;
            for (String c : contains) if (t.contains(c)) return true;
            return false;
        }
    }

    public static String norm(CharSequence s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s.toString(), Normalizer.Form.NFC);
        return t.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    public static List<Step> parse(String text) {
        List<Step> steps = new ArrayList<>();
        if (text == null) return steps;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            Step s = new Step();
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.equals("BACK")) {
                s.type = BACK;
            } else if (upper.startsWith("WAIT")) {
                s.type = WAIT;
                try {
                    s.waitMs = (long) (Double.parseDouble(line.substring(4).trim()) * 1000);
                } catch (Exception e) {
                    s.waitMs = 1000;
                }
            } else {
                s.type = CLICK;
                if (line.startsWith("*")) {
                    s.type = TARGET;
                    line = line.substring(1).trim();
                } else if (line.startsWith("?")) {
                    s.optional = true;
                    line = line.substring(1).trim();
                }
                for (String part : line.split("\\|")) {
                    String p = part.trim();
                    if (p.startsWith("~")) {
                        String c = norm(p.substring(1));
                        if (!c.isEmpty()) s.contains.add(c);
                    } else {
                        String e = norm(p);
                        if (!e.isEmpty()) s.exact.add(e);
                    }
                }
                if (s.exact.isEmpty() && s.contains.isEmpty()) continue;
            }
            steps.add(s);
        }
        // Nếu không có dòng '*', coi bước bấm đầu tiên là bước chọn mục
        boolean hasTarget = false;
        for (Step s : steps) if (s.type == TARGET) hasTarget = true;
        if (!hasTarget) {
            for (Step s : steps) {
                if (s.type == CLICK) { s.type = TARGET; s.optional = false; break; }
            }
        }
        // Đưa bước chọn mục lên đầu
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).type == TARGET) {
                if (i > 0) steps.add(0, steps.remove(i));
                break;
            }
        }
        return steps;
    }

    // ----------------- Kịch bản mẫu -----------------

    public static final String PRESET_UNFOLLOW_PAGES =
            "# BỎ THEO DÕI TRANG\n" +
            "# Mở danh sách Trang bạn đang theo dõi / đã thích, rồi bấm ▶ trên nút nổi.\n" +
            "* Đang theo dõi | Following | Đã thích | Liked\n" +
            "? Bỏ theo dõi | Unfollow | Bỏ thích | Unlike | ~Bỏ theo dõi Trang | ~Unfollow Page\n" +
            "? Bỏ theo dõi | Unfollow | Xác nhận | Confirm\n";

    public static final String PRESET_LEAVE_GROUPS =
            "# RỜI NHÓM\n" +
            "# Mở Menu → Nhóm → Nhóm của bạn (danh sách nhóm đã tham gia), rồi bấm ▶.\n" +
            "# Nếu không chạy: mở menu ⋯ của 1 nhóm, bấm 🔍 để dò tên nút thật rồi sửa dòng '*'.\n" +
            "* ~Tùy chọn khác | ~Thêm tùy chọn | ~More options | ~Xem tùy chọn\n" +
            "~Rời nhóm | ~Leave group\n" +
            "? Rời nhóm | Leave group | Rời khỏi nhóm | Leave\n";

    public static final String PRESET_LEAVE_GROUPS_INSIDE =
            "# RỜI NHÓM (cách 2: vào từng nhóm)\n" +
            "# Mở danh sách nhóm. Mỗi lượt: mở nhóm → bấm 'Đã tham gia' → Rời nhóm → quay lại.\n" +
            "# Dòng '*' cần là tên nút/ô của từng nhóm trong danh sách — dùng 🔍 để dò rồi sửa.\n" +
            "* ~Ảnh đại diện của nhóm | ~Group profile picture\n" +
            "WAIT 2\n" +
            "Đã tham gia | Joined | ~Đã tham gia\n" +
            "~Rời nhóm | ~Leave group\n" +
            "? Rời nhóm | Leave group | Rời khỏi nhóm\n" +
            "WAIT 2\n" +
            "BACK\n";
}
