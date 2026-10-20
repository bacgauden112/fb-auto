package vn.fbauto;

import android.content.Context;
import android.content.SharedPreferences;

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
}
