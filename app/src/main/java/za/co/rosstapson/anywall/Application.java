package za.co.rosstapson.anywall;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.parse.Parse;
import com.parse.ParseObject;

/**
 * Created by Ross on 12/10/2015.
 */
public class Application extends android.app.Application {
    public static final boolean APPDEBUG = true;
    public static final String APPTAG = "AnyWall";
    public static final String INTENT_EXTRA_LOCATION = "location";

    private static final String KEY_SEARCH_DISTANCE = "searchDistance";
    private static final float DEFAULT_SEARCH_DISTANCE = 250.0f;
    private static SharedPreferences preferences;
    private static ConfigHelper configHelper;

    public Application () {

    }
    @Override
    public void onCreate() {
        super.onCreate();
        if (APPDEBUG) {
            Log.d(APPTAG, "zomg! oncreate.");
        }
        Parse.enableLocalDatastore(this);
        Parse.initialize(this, "GMX4TuOIyCmHhG8Lr5G0wevl5gUEUHQpZeMRRdzh", "wqNviY1DyM6tqYhd3Wrb5vAdUP9NCv3Lkt6gIw8E");
        ParseObject.registerSubclass(AnywallPost.class);

        preferences = getSharedPreferences("za.co.rosstapson.anywall", Context.MODE_PRIVATE);
        configHelper = new ConfigHelper();
        configHelper.fetchConfigIfNeeded();
    }
    public static float getSearchDistance() {
        return preferences.getFloat(KEY_SEARCH_DISTANCE, DEFAULT_SEARCH_DISTANCE);
    }
    public static ConfigHelper getConfigHelper() {
        return configHelper;
    }
    public static void setSearchDistance(float value) {
        preferences.edit().putFloat(KEY_SEARCH_DISTANCE, value).commit();
    }
}
