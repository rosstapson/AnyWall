package za.co.rosstapson.anywall;

import com.parse.ParseClassName;
import com.parse.ParseGeoPoint;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;

/**
 * Created by Ross on 12/10/2015.
 */
@ParseClassName("Posts")
public class AnywallPost extends ParseObject {
    public String getText() {
        return getString("text");
    }
    public void setText(String value) {
        put("text", value);
    }
    public ParseUser getUser() {
        return getParseUser("user");
    }
    public void setUser(ParseUser value) {
        put("user", value);
    }
    public ParseGeoPoint getLocation() {
        return getParseGeoPoint("location");
    }
    public void setLocation(ParseGeoPoint value) {
        put("location", value);
    }
    public static ParseQuery<AnywallPost> getQuery() {
        return ParseQuery.getQuery(AnywallPost.class);
    }
}
