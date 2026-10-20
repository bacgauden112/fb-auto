package vn.fbauto;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class AutoService extends AccessibilityService {

    public static final String[] FB_PACKAGES = {"com.facebook.katana", "com.facebook.lite"};
    static final String[] LIMIT_WORDS = {
            "thử lại sau", "try again later", "tạm thời bị chặn", "temporarily blocked",
            "hạn chế hoạt động", "we limit how often"
    };

    static volatile AutoService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final Set<String> done = new HashSet<>();
    private final Set<String> doneNames = new HashSet<>();
    /** Nếu khác null: chỉ xử lý các nhóm có tên (chuẩn hóa) nằm trong tập này. */
    private volatile Set<String> onlyNames;
    private final LinkedList<String> logs = new LinkedList<>();

    private volatile boolean running;
    private Thread worker;
    private int count;
    private int scrollIndex;
    private String lastMsg = "Sẵn sàng";

    private WindowManager wm;
    private View overlay;
    private TextView statusView;
    private Button runBtn;

    // ================= vòng đời =================

    @Override
    protected void onServiceConnected() {
        instance = this;
        showOverlay();
        status("Sẵn sàng — mở Facebook rồi bấm ▶");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        stopRun();
        instance = null;
        if (overlay != null) {
            try { wm.removeView(overlay); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    // ================= nút nổi =================

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private Button smallBtn(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(dp(44));
        b.setMinHeight(0);
        b.setMinimumHeight(dp(40));
        b.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(40));
        lp.leftMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private void showOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(10), dp(6), dp(6), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6111111);
        bg.setCornerRadius(dp(14));
        box.setBackground(bg);

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(12);
        statusView.setMaxWidth(dp(170));
        statusView.setText("FB Auto");

        runBtn = smallBtn("▶");
        runBtn.setOnClickListener(v -> {
            if (running) stopRun(); else startRun();
        });
        Button dumpBtn = smallBtn("🔍");
        dumpBtn.setOnClickListener(v -> dumpScreen());
        Button scanBtn = smallBtn("📋");
        scanBtn.setOnClickListener(v -> {
            if (running) stopRun(); else startScan();
        });

        box.addView(statusView);
        box.addView(runBtn);
        box.addView(scanBtn);
        box.addView(dumpBtn);

        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(8);
        lp.y = dp(140);

        // Kéo chữ trạng thái để di chuyển nút nổi
        statusView.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy;
            int ox, oy;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX(); sy = e.getRawY(); ox = lp.x; oy = lp.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = ox + (int) (e.getRawX() - sx);
                        lp.y = oy + (int) (e.getRawY() - sy);
                        try { wm.updateViewLayout(overlay, lp); } catch (Exception ignored) { }
                        return true;
                }
                return false;
            }
        });

        overlay = box;
        try {
            wm.addView(box, lp);
        } catch (Exception e) {
            overlay = null;
        }
    }

    private void status(String msg) {
        lastMsg = msg;
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        String joined;
        synchronized (logs) {
            logs.addFirst(time + "  " + msg);
            while (logs.size() > 80) logs.removeLast();
            joined = String.join("\n", logs);
        }
        Config.prefs(this).edit().putString("runlog", joined).apply();
        main.post(() -> {
            if (statusView != null) statusView.setText("Đã xong: " + count + "\n" + msg);
            if (runBtn != null) runBtn.setText(running ? "■" : "▶");
        });
    }

    private void toast(String msg) {
        main.post(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    // ================= chạy / dừng =================

    void startRun() {
        if (running) return;
        final Config c = Config.load(this);
        final List<Recipe.Step> steps = Recipe.parse(c.recipe);
        if (steps.isEmpty() || steps.get(0).type != Recipe.TARGET) {
            toast("Kịch bản chưa hợp lệ — mở app FB Auto để sửa.");
            return;
        }
        running = true;
        count = 0;
        worker = new Thread(() -> {
            try {
                loop(c, steps);
            } catch (InterruptedException ignored) {
                lastMsg = "Bạn đã dừng";
            } catch (Exception e) {
                lastMsg = "Lỗi: " + e;
            } finally {
                running = false;
                status("■ " + lastMsg + " (tổng " + count + ")");
            }
        }, "fbauto-worker");
        worker.start();
        status("Bắt đầu...");
    }

    void stopRun() {
        running = false;
        if (worker != null) worker.interrupt();
    }

    private void loop(Config c, List<Recipe.Step> steps) throws InterruptedException {
        done.clear();
        doneNames.clear();
        int fails = 0, endCount = 0;
        boolean wrapped = false;
        Recipe.Step first = steps.get(0);
        // Đã quét danh sách -> chỉ xử lý các nhóm được tick
        Set<String> sel = Config.loadSelected(this);
        boolean hasScan = !Config.loadScanned(this).isEmpty();
        onlyNames = (first.row && hasScan) ? sel : null;
        if (onlyNames != null) {
            if (onlyNames.isEmpty()) { lastMsg = "Chưa tick nhóm nào trong app FB Auto"; return; }
            status("Chỉ rời " + onlyNames.size() + " nhóm đã chọn");
        }

        while (running && count < c.maxCount) {
            if (onlyNames != null && doneNames.containsAll(onlyNames)) {
                lastMsg = "Đã xử lý hết các nhóm đã chọn";
                return;
            }
            waitForFacebook();

            AccessibilityNodeInfo target = findTarget(first);
            if (target == null) {
                String before = signature();
                status("Đang cuộn tìm thêm...");
                scrollForward();
                nap(2000);
                if (before.equals(signature())) {
                    if (++endCount >= 3) {
                        if (onlyNames != null && !wrapped) {
                            status("Cuộn lên đầu tìm các nhóm còn lại...");
                            scrollToTop();
                            wrapped = true;
                            endCount = 0;
                            continue;
                        }
                        lastMsg = onlyNames != null ? "Không tìm thấy một số nhóm đã chọn" : "Hết mục trong danh sách";
                        return;
                    }
                } else {
                    endCount = 0;
                }
                continue;
            }
            endCount = 0;

            String key = first.row ? rowKey(target, first) : keyOf(target, first);
            done.add(key);
            if (first.row) doneNames.add(firstText(key));
            String name = shortName(key);
            String before = signature();
            // Chữ của vài mục khác trong danh sách — dùng để nhận ra đã quay về danh sách chưa
            List<String> anchors = first.row ? rowAnchors(first, key) : new ArrayList<>();
            status("→ " + name);

            click(target);
            nap(rand(800, 1400));

            boolean ok = true;
            for (int i = 1; i < steps.size() && running; i++) {
                Recipe.Step s = steps.get(i);
                if (s.type == Recipe.BACK) {
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    nap(1300);
                    continue;
                }
                if (s.type == Recipe.WAIT) {
                    nap(s.waitMs);
                    continue;
                }
                if (s.type == Recipe.RETURN) {
                    returnToList(anchors);
                    continue;
                }
                AccessibilityNodeInfo n = waitFor(s, s.optional ? c.optionalTimeoutMs : c.stepTimeoutMs);
                if (n == null) {
                    if (s.optional) continue;
                    ok = false;
                    break;
                }
                nap(rand(300, 700));
                click(n);
                nap(rand(900, 1500));
            }
            if (!running) return;

            if (screenContains(LIMIT_WORDS)) {
                lastMsg = "Facebook báo giới hạn — hãy nghỉ vài giờ";
                return;
            }

            if (ok) {
                count++;
                fails = 0;
                if (first.row) Config.removeGroup(this, firstText(key));
                status("✓ " + name);
            } else {
                fails++;
                status("✗ Không làm được: " + name);
                if (!anchors.isEmpty()) {
                    returnToList(anchors);
                } else if (!before.equals(signature())) {
                    // Chỉ bấm Quay lại nếu có gì đó (menu/hộp thoại) đã mở ra
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    nap(1000);
                }
                if (fails >= 3) {
                    lastMsg = "Lỗi 3 lần liên tiếp — dùng 🔍 kiểm tra tên nút";
                    return;
                }
            }

            if (count >= c.maxCount) { lastMsg = "Đủ " + c.maxCount + " mục"; return; }

            if (ok && c.pauseEvery > 0 && count % c.pauseEvery == 0) {
                status("Nghỉ " + c.pauseMinutes + " phút cho an toàn...");
                nap(c.pauseMinutes * 60_000L);
            } else {
                nap(rand(c.minDelay * 1000, c.maxDelay * 1000));
            }
        }
    }

    // ================= tiện ích =================

    private void nap(long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (!running) throw new InterruptedException();
            Thread.sleep(Math.min(250, Math.max(1, end - System.currentTimeMillis())));
        }
        if (!running) throw new InterruptedException();
    }

    private int rand(int a, int b) {
        return a + rnd.nextInt(Math.max(1, b - a + 1));
    }

    private static boolean isFb(CharSequence pkg) {
        if (pkg == null) return false;
        for (String p : FB_PACKAGES) if (p.contentEquals(pkg)) return true;
        return false;
    }

    private void waitForFacebook() throws InterruptedException {
        boolean told = false;
        while (true) {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r != null && isFb(r.getPackageName())) return;
            if (!told) { status("Tạm chờ: hãy mở app Facebook"); told = true; }
            nap(1000);
        }
    }

    /** Gốc của các cửa sổ Facebook đang hiển thị (cửa sổ trên cùng trước). */
    private List<AccessibilityNodeInfo> roots() {
        List<AccessibilityNodeInfo> rs = new ArrayList<>();
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null && isFb(r.getPackageName())) rs.add(r);
            }
        } catch (Exception ignored) { }
        if (rs.isEmpty()) {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r != null) rs.add(r);
        }
        return rs;
    }

    private List<AccessibilityNodeInfo> nodes(AccessibilityNodeInfo root, int limit) {
        List<AccessibilityNodeInfo> out = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty() && out.size() < limit) {
            AccessibilityNodeInfo n = q.poll();
            out.add(n);
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo ch = n.getChild(i);
                if (ch != null) q.add(ch);
            }
        }
        return out;
    }

    private List<AccessibilityNodeInfo> allNodes() {
        List<AccessibilityNodeInfo> out = new ArrayList<>();
        for (AccessibilityNodeInfo r : roots()) out.addAll(nodes(r, 3000));
        return out;
    }

    private boolean visible(AccessibilityNodeInfo n) {
        if (!n.isVisibleToUser()) return false;
        Rect b = new Rect();
        n.getBoundsInScreen(b);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return b.width() > 0 && b.height() > 0 && b.bottom > 0 && b.top < dm.heightPixels;
    }

    private static boolean matches(AccessibilityNodeInfo n, Recipe.Step s) {
        return s.matches(Recipe.norm(n.getText())) || s.matches(Recipe.norm(n.getContentDescription()));
    }

    private AccessibilityNodeInfo findTarget(Recipe.Step s) {
        if (s.row) {
            List<AccessibilityNodeInfo> rows = rowCandidates(s);
            return rows.isEmpty() ? null : rows.get(0);
        }
        AccessibilityNodeInfo best = null;
        int bestTop = Integer.MAX_VALUE;
        Rect b = new Rect();
        for (AccessibilityNodeInfo n : allNodes()) {
            if (!matches(n, s) || !visible(n)) continue;
            String k = keyOf(n, s);
            if (done.contains(k) || s.skipped(k)) continue;
            n.getBoundsInScreen(b);
            if (b.top < bestTop) { bestTop = b.top; best = n; }
        }
        return best;
    }

    private AccessibilityNodeInfo waitFor(Recipe.Step s, long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            List<AccessibilityNodeInfo> all = allNodes();
            for (AccessibilityNodeInfo n : all) {
                if ((s.matchesExact(Recipe.norm(n.getText())) || s.matchesExact(Recipe.norm(n.getContentDescription())))
                        && visible(n)) return n;
            }
            for (AccessibilityNodeInfo n : all) {
                if (matches(n, s) && visible(n)) return n;
            }
            nap(250);
        }
        return null;
    }

    /** Khóa nhận diện 1 mục: chữ của hàng chứa nút (tên Trang/nhóm). */
    private String keyOf(AccessibilityNodeInfo n, Recipe.Step step) {
        AccessibilityNodeInfo p = n;
        for (int lvl = 0; lvl < 6; lvl++) {
            p = p.getParent();
            if (p == null) break;
            String t = collectText(p, step);
            if (t.length() >= 2) return t;
        }
        Rect b = new Rect();
        n.getBoundsInScreen(b);
        return "#" + b.toShortString() + "#" + scrollIndex;
    }

    private String collectText(AccessibilityNodeInfo root, Recipe.Step skip) {
        StringBuilder sb = new StringBuilder();
        for (AccessibilityNodeInfo n : nodes(root, 40)) {
            String t = Recipe.norm(n.getText());
            if (t.isEmpty() || skip.matches(t)) continue;
            sb.append(t).append(" | ");
            if (sb.length() > 120) break;
        }
        return sb.toString();
    }

    // ---------- chế độ ROW: tự chọn từng hàng trong danh sách ----------

    /** Danh sách cuộn lớn nhất đang hiển thị (thường là danh sách chính). */
    private AccessibilityNodeInfo mainList() {
        AccessibilityNodeInfo best = null;
        long bestArea = 0;
        Rect b = new Rect();
        for (AccessibilityNodeInfo n : allNodes()) {
            if (!n.isScrollable() || !visible(n)) continue;
            n.getBoundsInScreen(b);
            long area = (long) b.width() * b.height();
            if (area > bestArea) { bestArea = area; best = n; }
        }
        return best;
    }

    /** Các hàng bấm được trong danh sách chính, chưa xử lý, xếp từ trên xuống. */
    private List<AccessibilityNodeInfo> rowCandidates(Recipe.Step s) {
        List<AccessibilityNodeInfo> out = new ArrayList<>();
        AccessibilityNodeInfo list = mainList();
        if (list == null) return out;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        Rect lb = new Rect();
        list.getBoundsInScreen(lb);
        final List<int[]> tops = new ArrayList<>();
        Rect b = new Rect();
        for (AccessibilityNodeInfo n : nodes(list, 1500)) {
            if (n == list || !n.isClickable() || !visible(n)) continue;
            n.getBoundsInScreen(b);
            if (b.width() < dm.widthPixels * 0.3 || b.height() < dp(40)) continue;
            if (b.top < lb.top - 2 || b.bottom > lb.bottom + dp(4)) continue; // bị che một phần
            String k = rowKey(n, s);
            if (k.length() <= 4 || done.contains(k) || s.skipped(k) || doneNames.contains(firstText(k))) continue;
            if (onlyNames != null && !onlyNames.contains(firstText(k))) continue;
            out.add(n);
            tops.add(new int[]{b.top, -b.width() * b.height()});
        }
        // Bỏ các ô nằm lọt trong một ô khác (giữ ô ngoài cùng = cả hàng)
        List<Rect> rects = new ArrayList<>();
        for (AccessibilityNodeInfo n : out) { Rect r = new Rect(); n.getBoundsInScreen(r); rects.add(r); }
        for (int i = out.size() - 1; i >= 0; i--) {
            for (int j = 0; j < out.size(); j++) {
                if (i != j && rects.get(j).contains(rects.get(i)) && !rects.get(j).equals(rects.get(i))) {
                    out.remove(i); tops.remove(i); rects.remove(i);
                    break;
                }
            }
        }
        // Sắp theo vị trí từ trên xuống; cùng hàng thì ưu tiên ô lớn hơn
        Integer[] idx = new Integer[out.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (x, y) -> tops.get(x)[0] != tops.get(y)[0]
                ? Integer.compare(tops.get(x)[0], tops.get(y)[0])
                : Integer.compare(tops.get(x)[1], tops.get(y)[1]));
        List<AccessibilityNodeInfo> sorted = new ArrayList<>();
        for (Integer i : idx) sorted.add(out.get(i));
        return sorted;
    }

    private String rowKey(AccessibilityNodeInfo n, Recipe.Step s) {
        StringBuilder sb = new StringBuilder("row:");
        for (AccessibilityNodeInfo c : nodes(n, 40)) {
            String t = Recipe.norm(c.getText());
            if (t.isEmpty()) t = Recipe.norm(c.getContentDescription());
            if (t.isEmpty()) continue;
            sb.append(t).append(" | ");
            if (sb.length() > 120) break;
        }
        return sb.toString();
    }

    private List<String> rowAnchors(Recipe.Step s, String exceptKey) {
        List<String> a = new ArrayList<>();
        AccessibilityNodeInfo list = mainList();
        if (list == null) return a;
        for (AccessibilityNodeInfo n : nodes(list, 1500)) {
            String t = Recipe.norm(n.getText());
            if (t.length() < 3 || exceptKey.contains(t) || a.contains(t)) continue;
            a.add(t);
            if (a.size() >= 8) break;
        }
        return a;
    }

    private boolean onList(List<String> anchors) {
        int hits = 0;
        for (AccessibilityNodeInfo n : allNodes()) {
            String t = Recipe.norm(n.getText());
            if (!t.isEmpty() && anchors.contains(t) && ++hits >= 2) return true;
        }
        return false;
    }

    /** Bấm Quay lại (tối đa 4 lần) cho đến khi thấy lại danh sách ban đầu. */
    private void returnToList(List<String> anchors) throws InterruptedException {
        if (anchors.isEmpty()) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            nap(1500);
            return;
        }
        for (int i = 0; i < 4; i++) {
            if (onList(anchors)) return;
            performGlobalAction(GLOBAL_ACTION_BACK);
            nap(1600);
        }
        if (!onList(anchors)) status("⚠ Không chắc đã quay về danh sách");
    }

    private static String firstText(String key) {
        if (key.startsWith("row:")) key = key.substring(4);
        int i = key.indexOf(" | ");
        return i > 0 ? key.substring(0, i) : key;
    }

    private static String shortName(String key) {
        if (key.startsWith("#")) return "(mục)";
        if (key.startsWith("row:")) key = key.substring(4);
        int i = key.indexOf(" | ");
        String s = i > 0 ? key.substring(0, i) : key;
        return s.length() > 32 ? s.substring(0, 32) + "…" : s;
    }

    private String signature() {
        StringBuilder sb = new StringBuilder();
        int k = 0;
        for (AccessibilityNodeInfo n : allNodes()) {
            CharSequence t = n.getText();
            if (t != null && t.length() > 0) {
                sb.append(t).append('|');
                if (++k > 80) break;
            }
        }
        return sb.toString();
    }

    private boolean screenContains(String[] words) {
        for (AccessibilityNodeInfo n : allNodes()) {
            String t = Recipe.norm(n.getText()) + " " + Recipe.norm(n.getContentDescription());
            for (String w : words) if (t.contains(w)) return true;
        }
        return false;
    }

    private void click(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo c = n;
        int guard = 0;
        while (c != null && !c.isClickable() && guard++ < 3) c = c.getParent();
        if (c != null && c.isClickable() && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
        Rect b = new Rect();
        n.getBoundsInScreen(b);
        tap(b.centerX(), b.centerY());
    }

    private void tap(int x, int y) {
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 60))
                .build();
        dispatchGesture(g, null, null);
    }

    private void swipeUp() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        Path p = new Path();
        p.moveTo(dm.widthPixels / 2f, dm.heightPixels * 0.75f);
        p.lineTo(dm.widthPixels / 2f, dm.heightPixels * 0.35f);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 450))
                .build();
        dispatchGesture(g, null, null);
    }

    private void scrollForward() {
        scrollIndex++;
        AccessibilityNodeInfo best = null;
        long bestArea = 0;
        Rect b = new Rect();
        for (AccessibilityNodeInfo n : allNodes()) {
            if (!n.isScrollable() || !visible(n)) continue;
            n.getBoundsInScreen(b);
            long area = (long) b.width() * b.height();
            if (area > bestArea) { bestArea = area; best = n; }
        }
        if (best != null && best.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return;
        swipeUp();
    }

    // ================= quét danh sách nhóm =================

    void startScan() {
        if (running) return;
        running = true;
        worker = new Thread(() -> {
            try {
                scan();
            } catch (InterruptedException ignored) {
                lastMsg = "Đã dừng quét";
            } catch (Exception e) {
                lastMsg = "Lỗi: " + e;
            } finally {
                running = false;
                status("■ " + lastMsg);
            }
        }, "fbauto-scan");
        worker.start();
        status("Bắt đầu quét...");
    }

    private void scan() throws InterruptedException {
        waitForFacebook();
        Recipe.Step rowStep = new Recipe.Step();
        rowStep.row = true;
        List<Recipe.Step> steps = Recipe.parse(Config.load(this).recipe);
        if (!steps.isEmpty()) rowStep.skip.addAll(steps.get(0).skip);
        done.clear();
        doneNames.clear();
        onlyNames = null;

        LinkedHashMap<String, String[]> found = new LinkedHashMap<>();
        int same = 0;
        while (running && same < 3) {
            for (AccessibilityNodeInfo n : rowCandidates(rowStep)) {
                String[] t = rowTexts(n);
                String k = Recipe.norm(t[0]);
                if (!k.isEmpty() && !found.containsKey(k)) found.put(k, t);
            }
            status("Đang quét: " + found.size() + " nhóm...");
            String sig = signature();
            scrollForward();
            nap(1800);
            if (sig.equals(signature())) same++; else same = 0;
        }
        if (found.isEmpty()) {
            lastMsg = "Không thấy nhóm nào — hãy mở 'Nhóm của bạn' rồi quét lại";
            return;
        }
        Config.saveScanned(this, new ArrayList<>(found.values()));
        // Giữ lại các lựa chọn cũ còn tồn tại
        Set<String> sel = Config.loadSelected(this);
        sel.retainAll(found.keySet());
        Config.saveSelected(this, sel);

        status("Quét xong " + found.size() + " nhóm — đang cuộn lên đầu...");
        scrollToTop();
        lastMsg = "Quét xong " + found.size() + " nhóm — chọn nhóm trong app FB Auto";
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra("showPicker", true);
            startActivity(i);
        } catch (Exception e) {
            toast("Quét xong. Mở app FB Auto để chọn nhóm.");
        }
    }

    /** 2 dòng chữ đầu tiên của 1 hàng (giữ nguyên chữ hoa/thường). */
    private String[] rowTexts(AccessibilityNodeInfo n) {
        String[] r = {"", ""};
        int k = 0;
        for (AccessibilityNodeInfo c : nodes(n, 40)) {
            CharSequence t = c.getText();
            if (t == null || Recipe.norm(t).isEmpty()) t = c.getContentDescription();
            if (t == null || Recipe.norm(t).isEmpty()) continue;
            r[k++] = t.toString().replaceAll("\\s+", " ").trim();
            if (k == 2) break;
        }
        return r;
    }

    private void scrollToTop() throws InterruptedException {
        for (int i = 0; i < 40 && running; i++) {
            String sig = signature();
            AccessibilityNodeInfo list = mainList();
            if (list == null || !list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) swipeDown();
            nap(700);
            if (sig.equals(signature())) return;
        }
    }

    private void swipeDown() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        Path p = new Path();
        p.moveTo(dm.widthPixels / 2f, dm.heightPixels * 0.35f);
        p.lineTo(dm.widthPixels / 2f, dm.heightPixels * 0.8f);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 300))
                .build();
        dispatchGesture(g, null, null);
    }

    // ================= dò màn hình =================

    private void dumpScreen() {
        AccessibilityNodeInfo active = getRootInActiveWindow();
        StringBuilder sb = new StringBuilder();
        sb.append("App: ").append(active != null ? active.getPackageName() : "?")
          .append("  lúc ").append(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()))
          .append("\n[C]=bấm được  [S]=cuộn được\n\n");
        List<AccessibilityNodeInfo> list = allNodes();
        if (list.isEmpty() && active != null) list = nodes(active, 3000);
        int shown = 0;
        Rect b = new Rect();
        for (AccessibilityNodeInfo n : list) {
            if (!n.isVisibleToUser()) continue;
            CharSequence t = n.getText(), d = n.getContentDescription();
            boolean hasT = t != null && t.length() > 0, hasD = d != null && d.length() > 0;
            if (!hasT && !hasD && !n.isClickable()) continue;
            n.getBoundsInScreen(b);
            sb.append(n.isClickable() ? "[C]" : "   ")
              .append(n.isScrollable() ? "[S] " : " ");
            if (hasT) sb.append('"').append(t).append('"');
            if (hasD) sb.append(" desc=\"").append(d).append('"');
            sb.append("  @").append(b.left).append(',').append(b.top).append('\n');
            if (++shown >= 400) break;
        }
        String out = sb.toString();
        Config.prefs(this).edit().putString("dump", out).apply();
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("FB Auto dump", out));
        } catch (Exception ignored) { }
        toast("Đã dò " + shown + " phần tử (đã chép vào bộ nhớ tạm). Mở app FB Auto để xem.");
    }
}
