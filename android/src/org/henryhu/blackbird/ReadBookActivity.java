package org.henryhu.blackbird;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;


public class ReadBookActivity extends Activity {
	String book_id;
	String book_title;
	int book_size;
	TextView book_text;
	Button next, prev, jumpto;
	int cur_pos;
    DefaultHttpClient http_client = new DefaultHttpClient();
	SharedPreferences prefs;
	ScrollView book_scroll;
	final int page_size = 1024;

	Semaphore idle = new Semaphore(1);
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.read_book);
        prefs = getSharedPreferences("data", 0);

		book_id = getIntent().getExtras().getString("book_id");
		book_title = getIntent().getExtras().getString("book_title");
		book_size = getIntent().getExtras().getInt("book_size");
		
		book_text = (TextView)findViewById(R.id.read_text);
		next = (Button)findViewById(R.id.read_next);
		prev = (Button)findViewById(R.id.read_prev);
		jumpto = (Button)findViewById(R.id.read_jump);
		book_scroll = (ScrollView)findViewById(R.id.read_scroll);
		
		BasicClientCookie cookie = new BasicClientCookie("SACSID", prefs.getString("SACSID", ""));
        cookie.setSecure(true);
        cookie.setDomain(Network.svr_domain);
        cookie.setPath("/");
        
        http_client.getCookieStore().addCookie(cookie);
		
		next.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				nextPage();
			}
		});
		prev.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				prevPage();
			}
		});
		jumpto.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				
			}
		});
		
		cur_pos = 0;
		loadPage();
	}
	
	void loadPage() {
		new LoadBookTask().execute(new Book(book_title, book_size, book_id), cur_pos, cur_pos + page_size);
	}
	
	void nextPage() {
		cur_pos += page_size;
		if (cur_pos >= book_size)
			cur_pos = book_size;
		loadPage();
	}
	
	void prevPage() {
		cur_pos -= page_size;
		if (cur_pos < 0)
			cur_pos = 0;
		loadPage();
	}
	
	private class LoadBookTask extends AsyncTask<Object, Object, HttpResponse> {
		int start, end;
    	@Override
    	protected HttpResponse doInBackground(Object... args) {
    		try {
				idle.acquire();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		Book book = (Book)args[0];
    		start = (Integer)args[1];
    		end = (Integer)args[2];
    		
    		List<NameValuePair> params = new ArrayList<NameValuePair>();
    		params.add(new BasicNameValuePair("id", book.id));
    		params.add(new BasicNameValuePair("start", String.valueOf(start))); 
    		params.add(new BasicNameValuePair("end", String.valueOf(end)));
    		
    		return Network.get(http_client, Network.bookget_url, params);
    	}
    	
    	protected void onPostExecute(HttpResponse result) {
    		Log.d("LoadBookTask", "PostExecute()");
    		String sret = Network.readResponse(result);
    		if (sret == null) {
    			showMsg("fail to read book");
    			Log.d("LoadBookTask", "No result");
    			idle.release();
    			return;
    		}
    		
//    		Log.d("LoadBookTask", "Result: " + sret);
    		byte[] data = Base64.decode(sret, Base64.DEFAULT);
//    		Log.d("LoadBookTask", "Result: " + new String(data));
//   			Toast.makeText(getApplicationContext(), new String(data), Toast.LENGTH_LONG).show();
   			try {
				book_text.setText(new String(data, "gbk"));
				book_scroll.smoothScrollTo(0,0);
			} catch (UnsupportedEncodingException e) { }
   			cur_pos = start;
   			
   			
    		idle.release();
    	}
    }
	
	void showMsg(String msg) {
		Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG);
	}
}
