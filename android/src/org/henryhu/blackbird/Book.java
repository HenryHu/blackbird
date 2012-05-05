package org.henryhu.blackbird;

import org.json.JSONException;
import org.json.JSONObject;

public class Book {
	String title;
	int size;
	

	public Book(JSONObject obj) throws JSONException {
		title = (String)obj.get("title");
		size = (int)obj.getInt("size");
		
	}
	
	public String toString() {
		return String.format("%s   %db", title, size);
	}
}
