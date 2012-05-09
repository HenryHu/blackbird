package org.henryhu.blackbird;

import org.json.JSONException;
import org.json.JSONObject;

public class Book {
	String title;
	int size;
	String id;
    int place;

	public Book(JSONObject obj) throws JSONException {
		title = obj.getString("title");
		size = obj.getInt("size");
		id = obj.getString("id");
        place = obj.getInt("place");
	}
	
	public Book(String _title, int _size, String _id, int _place) {
		title = _title;
		size = _size;
		id = _id;
        place = _place;
	}
	
	public String toString() {
		return String.format("%s   %db", title, size);
	}
}
