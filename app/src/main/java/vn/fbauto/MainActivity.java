package vn.fbauto;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private EditText recipe, minDelay, maxDelay, maxCount, pauseEvery, pauseMinutes;
    private TextView svcStatus, logView, dumpView;
    private LinearLayout col, pickerBox;
    private ScrollView sv;
    private TextView pickerHeader, pickerCount;
    private final List<CheckBox> boxes = new ArrayList<>();

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sv = new ScrollView(this);
        sv.setBackgroundColor(Color.WHITE);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(20), dp(16), dp(40));
        sv.addView(col);
        setContentView(sv);

        TextView title = text("FB Auto", 24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(title);

        svcStatus = text("", 14);
        svcStatus.setPadding(0, dp(6), 0, dp(6));
        col.addView(svcStatus);

        Button openA11y = button("Bật quyền Trợ năng cho FB Auto");
        openA11y.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        col.addView(openA11y);

        col.addView(note(
                "Cách dùng:\n" +
                "1. Bấm nút trên → Ứng dụng đã cài đặt → FB Auto → Bật.\n" +
                "   (Android 13+: nếu bị mờ, vào Cài đặt → Ứng dụng → FB Auto → ⋮ → \"Cho phép cài đặt bị hạn chế\" rồi bật lại.)\n" +
                "2. Chọn kịch bản bên dưới → Lưu.\n" +
                "3. Mở Facebook, vào đúng màn hình danh sách, bấm ▶ trên nút nổi. Bấm ■ để dừng.\n" +
                "4. Nếu không chạy: mở màn hình cần bấm, bấm 🔍 để dò tên nút, quay lại đây xem và sửa kịch bản."));

        // ---------- chọn nhóm ----------
        pickerHeader = header("Chọn nhóm để rời");
        col.addView(note(
                "1. Mở Facebook → Menu → Nhóm → 'Nhóm của bạn', bấm 📋 trên nút nổi để quét.\n" +
                "2. Tick các nhóm muốn rời → 'Lưu & mở Facebook' → bấm ▶.\n" +
                "Khi đã có danh sách quét, ▶ CHỈ rời các nhóm được tick. " +
                "Bấm 'Xóa danh sách quét' để quay về chế độ rời tất cả."));
        pickerCount = text("", 14);
        pickerCount.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(pickerCount);
        LinearLayout pickBtns = new LinearLayout(this);
        pickBtns.setOrientation(LinearLayout.HORIZONTAL);
        Button all = button("Chọn tất cả");
        all.setOnClickListener(v -> { for (CheckBox b : boxes) b.setChecked(true); });
        Button none = button("Bỏ chọn hết");
        none.setOnClickListener(v -> { for (CheckBox b : boxes) b.setChecked(false); });
        pickBtns.addView(all, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        pickBtns.addView(none, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        col.addView(pickBtns);
        pickerBox = new LinearLayout(this);
        pickerBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(pickerBox);
        Button saveSel = button("Lưu lựa chọn & mở Facebook");
        saveSel.setOnClickListener(v -> {
            saveSelection();
            openFacebook();
        });
        col.addView(saveSel);
        Button clearScan = button("Xóa danh sách quét");
        clearScan.setOnClickListener(v -> {
            Config.prefs(this).edit().remove("scanned").remove("selected").apply();
            refreshPicker();
        });
        col.addView(clearScan);

        header("Kịch bản");
        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.VERTICAL);
        presets.addView(presetBtn("Mẫu: Rời nhóm tự động (vào từng nhóm)", Recipe.PRESET_LEAVE_GROUPS_INSIDE));
        presets.addView(presetBtn("Mẫu: Rời nhóm (menu ⋯ trong danh sách)", Recipe.PRESET_LEAVE_GROUPS));
        presets.addView(presetBtn("Mẫu: Bỏ theo dõi Trang", Recipe.PRESET_UNFOLLOW_PAGES));
        col.addView(presets);

        recipe = new EditText(this);
        recipe.setTypeface(Typeface.MONOSPACE);
        recipe.setTextSize(13);
        recipe.setMinLines(7);
        recipe.setGravity(Gravity.TOP | Gravity.START);
        recipe.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        col.addView(recipe);

        col.addView(note(
                "Cú pháp (mỗi dòng 1 bước):\n" +
                "* A | B   bước đầu: tìm mục trong danh sách có nút A hoặc B\n" +
                "* ROW     bước đầu: tự mở lần lượt từng hàng trong danh sách\n" +
                "! A | B   bỏ qua mục có chữ A hoặc B (vd. nhóm muốn giữ lại)\n" +
                "A | B     bấm nút A hoặc B (bắt buộc)\n" +
                "? A | B   bấm nếu có (không bắt buộc)\n" +
                "~A        khớp khi chữ CHỨA A (không cần giống hệt)\n" +
                "BACK      bấm Quay lại      WAIT 2   chờ 2 giây\n" +
                "RETURN    quay lại cho tới khi về đúng danh sách\n" +
                "# ...     ghi chú"));

        header("Tốc độ (càng chậm càng an toàn)");
        minDelay = number("Chờ tối thiểu giữa 2 mục (giây)");
        maxDelay = number("Chờ tối đa giữa 2 mục (giây)");
        maxCount = number("Số mục tối đa mỗi lần chạy");
        pauseEvery = number("Nghỉ sau mỗi bao nhiêu mục (0 = không nghỉ)");
        pauseMinutes = number("Nghỉ bao lâu (phút)");

        Button save = button("Lưu cài đặt");
        save.setOnClickListener(v -> save(true));
        col.addView(save);

        header("Nhật ký lần chạy");
        logView = mono();
        col.addView(logView);

        header("Kết quả dò màn hình (🔍)");
        Button copy = button("Chép kết quả dò");
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("FB Auto dump", dumpView.getText()));
            Toast.makeText(this, "Đã chép", Toast.LENGTH_SHORT).show();
        });
        col.addView(copy);
        dumpView = mono();
        col.addView(dumpView);

        loadValues();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean on = AutoService.instance != null;
        svcStatus.setText(on ? "🟢 Trợ năng đang bật — nút nổi đã sẵn sàng"
                             : "🔴 Chưa bật quyền Trợ năng");
        SharedPreferences p = Config.prefs(this);
        logView.setText(p.getString("runlog", "(chưa có)"));
        dumpView.setText(p.getString("dump", "(chưa dò lần nào)"));
        refreshPicker();
        if (getIntent() != null && getIntent().getBooleanExtra("showPicker", false)) {
            getIntent().removeExtra("showPicker");
            sv.post(() -> sv.smoothScrollTo(0, pickerHeader.getTop()));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    // ---------- danh sách chọn nhóm ----------

    private void refreshPicker() {
        boxes.clear();
        pickerBox.removeAllViews();
        List<String[]> items = Config.loadScanned(this);
        Set<String> sel = Config.loadSelected(this);
        if (items.isEmpty()) {
            pickerBox.addView(note("(Chưa quét — ▶ sẽ rời TẤT CẢ nhóm, trừ các nhóm ở dòng '!')"));
        }
        for (String[] it : items) {
            CheckBox cb = new CheckBox(this);
            String key = Recipe.norm(it[0]);
            cb.setTag(key);
            cb.setText(it[1].isEmpty() ? it[0] : it[0] + "\n" + it[1]);
            cb.setTextSize(14);
            cb.setPadding(0, dp(6), 0, dp(6));
            cb.setChecked(sel.contains(key));
            cb.setOnCheckedChangeListener((b, c) -> updateCount());
            boxes.add(cb);
            pickerBox.addView(cb);
        }
        updateCount();
    }

    private void updateCount() {
        int n = 0;
        for (CheckBox b : boxes) if (b.isChecked()) n++;
        pickerCount.setText(boxes.isEmpty() ? "" : "Đã chọn " + n + " / " + boxes.size() + " nhóm");
    }

    private void saveSelection() {
        Set<String> sel = new LinkedHashSet<>();
        for (CheckBox b : boxes) if (b.isChecked()) sel.add((String) b.getTag());
        Config.saveSelected(this, sel);
        // Đảm bảo đang dùng kịch bản rời nhóm kiểu ROW
        if (!recipe.getText().toString().toUpperCase().contains("ROW")) {
            recipe.setText(Recipe.PRESET_LEAVE_GROUPS_INSIDE);
        }
        save(false);
        Toast.makeText(this, "Đã lưu " + sel.size() + " nhóm. Vào lại 'Nhóm của bạn' rồi bấm ▶", Toast.LENGTH_LONG).show();
    }

    private void openFacebook() {
        for (String pkg : AutoService.FB_PACKAGES) {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) { startActivity(i); return; }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        save(false);
    }

    // ---------- lưu / tải ----------

    private void loadValues() {
        Config c = Config.load(this);
        recipe.setText(c.recipe);
        minDelay.setText(String.valueOf(c.minDelay));
        maxDelay.setText(String.valueOf(c.maxDelay));
        maxCount.setText(String.valueOf(c.maxCount));
        pauseEvery.setText(String.valueOf(c.pauseEvery));
        pauseMinutes.setText(String.valueOf(c.pauseMinutes));
    }

    private int num(EditText e, int def) {
        try { return Integer.parseInt(e.getText().toString().trim()); }
        catch (Exception ex) { return def; }
    }

    private void save(boolean notify) {
        Config.prefs(this).edit()
                .putString("recipe", recipe.getText().toString())
                .putInt("minDelay", num(minDelay, 8))
                .putInt("maxDelay", num(maxDelay, 20))
                .putInt("maxCount", num(maxCount, 50))
                .putInt("pauseEvery", num(pauseEvery, 15))
                .putInt("pauseMinutes", num(pauseMinutes, 10))
                .apply();
        if (notify) Toast.makeText(this, "Đã lưu", Toast.LENGTH_SHORT).show();
    }

    // ---------- dựng giao diện ----------

    private TextView text(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(0xFF111111);
        return t;
    }

    private TextView note(String s) {
        TextView t = text(s, 13);
        t.setTextColor(0xFF555555);
        t.setPadding(0, dp(8), 0, dp(8));
        return t;
    }

    private TextView mono() {
        TextView t = text("", 11);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setBackgroundColor(0xFFF3F3F3);
        t.setPadding(dp(8), dp(8), dp(8), dp(8));
        return t;
    }

    private TextView header(String s) {
        TextView t = text(s, 17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(20), 0, dp(6));
        col.addView(t);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    private View presetBtn(String label, String preset) {
        Button b = button(label);
        b.setOnClickListener(v -> {
            recipe.setText(preset);
            Toast.makeText(this, "Đã nạp mẫu — nhớ bấm Lưu", Toast.LENGTH_SHORT).show();
        });
        return b;
    }

    private EditText number(String label) {
        col.addView(text(label, 13));
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        col.addView(e);
        return e;
    }
}
