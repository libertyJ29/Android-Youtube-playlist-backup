package com.example.YouTubePlaylistBackup;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 *  Class to store playlist items
 */
public class aPlaylistItem implements Serializable {
    String title;
    String description;
    long position;
    String videoId;

    public aPlaylistItem(String title, String description, long position, String videoId) {
        super();
        this.title = title;
        this.description = description;
        this.position = position;
        this.videoId = videoId;
    }

    public String getTitle(){
        return title;
    }
    public String getDescription(){
        return description;
    }
    public String getPosition(){
        return Long.toString(position);
    }
    public String getVideoId(){
        return videoId;
    }

    @Override
    public String toString() {

        // Convert the object to a JSON string for storage
        JSONObject json = new JSONObject();
        try {
            json.put("title", getTitle());
            json.put("description", getDescription());
            json.put("position", getPosition());
            json.put("videoId", getVideoId());
        }catch (JSONException e) {
            Log.e("aPlaylistItem to JSON", "unexpected JSON exception", e);
        }

        return json.toString();
    }
}

