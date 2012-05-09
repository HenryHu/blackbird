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
	
	String formatNum(int num) {
		if (num < 1000)
			return String.valueOf(num);
		else if (num < 1000000)
			return String.format("%.1fK", (double)num / 1000);
		else if (num < 1000000000)
			return String.format("%.1fM", (double)num / 1000000);
		else
			return String.format("%.1fG", (double)num / 1000000000);
	}
	
	public String toString() {
		if (size == 0)
			return String.format("%s      empty", title);
		else
			return String.format("%s      read: %.2f%%(%s)", title, (double)place / (double)size * 100, formatNum(size));
	}
}
