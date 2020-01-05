package com.android.settings.netdisk;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ListDataSaveUitl {
    /**
     * 保存List
     *
     * @param tag
     * @param datalist
     */
    public static <T> void saveListData(Context context, String prefName, String tag, List<T> datalist) {
        if (null == datalist || datalist.size() <= 0) {
            return;
        }

        SharedPreferences preferences = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        Gson gson = new Gson();
        String strJson = gson.toJson(datalist);
        editor.putString(tag, strJson);
        editor.apply();

    }

    /**
     * 获取List
     *
     * @param tag
     * @return
     */
    public static <T> List<T> getListData(Context context, String prefName, String tag) {
        SharedPreferences preferences = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
        List<T> datalist = new ArrayList<T>();
        String strJson = preferences.getString(tag, null);
        if (null == strJson) {
            return datalist;
        }
        Gson gson = new Gson();
        datalist = gson.fromJson(strJson, new TypeToken<List<T>>() {
        }.getType());
        return datalist;

    }

    public static <V> void saveMapData(Context context, String prefName, String key, Map<String, V> mapData) {
        SharedPreferences sp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();

        Gson gson = new Gson();
        String json = gson.toJson(mapData);
        editor.putString(key, json);
        editor.apply();
    }

    public static <V> HashMap<String, V> getMapData(Context context, String prefName, String key, Class<V> clsV) {
        HashMap<String, V> mapData = new HashMap<String, V>();
        SharedPreferences sp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
        String json = sp.getString(key, null);
        if (!TextUtils.isEmpty(json)) {
            Gson gson = new Gson();
            JsonObject obj = new JsonParser().parse(json).getAsJsonObject();
            Set<Map.Entry<String, JsonElement>> entrySet = obj.entrySet();
            for (Map.Entry<String, JsonElement> entry : entrySet) {
                String entryKey = entry.getKey();
                JsonElement value = entry.getValue();
                mapData.put(entryKey, gson.fromJson(value, clsV));
            }
        }
        return mapData;
    }
}
