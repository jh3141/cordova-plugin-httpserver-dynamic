package uk.org.dsf.cordova.dynamichttp;

import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

public abstract class JSONUtils
{
    public static List<String> arrayToStringList (JSONArray source) throws JSONException
    {
        ArrayList<String> result = new ArrayList<String> (source.length());
        for (int i = 0; i < source.length(); i ++)
            result.add (source.getString(i));
        return result;
    }
}
