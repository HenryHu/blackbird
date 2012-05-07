package org.henryhu.blackbird;

import org.json.JSONException;
import org.json.JSONObject;

public class Book {
	String title;
	int size;
	String id;

	public Book(JSONObject obj) throws JSONException {
		title = obj.getString("title");
		size = obj.getInt("size");
		id = obj.getString("id");
	}
	
	public Book(String _title, int _size, String _id) {
		title = _title;
		size = _size;
		id = _id;
	}
	
	public String toString() {
		return String.format("%s   %db", title, size);
	}
}
