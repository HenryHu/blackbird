package org.henryhu.blackbird;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;


public class BookListActivity extends Activity {
	SharedPreferences prefs;
    DefaultHttpClient http_client = new DefaultHttpClient();
    Button addBook;
    Button refreshBook;
    ListView bookList;
    ArrayAdapter bookListAdapter;
    List<Book> bookListStore;
    Semaphore idle;
	
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.book_list);
        
        prefs = getSharedPreferences("data", 0);
        if (prefs.getString("SACSID", "").equals("")) {
        	Intent intent = new Intent(this, LoginActivity.class);
        	startActivity(intent);
        	finish();
        }
        idle = new Semaphore(1);
        
        BasicClientCookie cookie = new BasicClientCookie("SACSID", prefs.getString("SACSID", ""));
        cookie.setSecure(true);
        cookie.setDomain(Network.svr_domain);
        cookie.setPath("/");
        
        http_client.getCookieStore().addCookie(cookie);
        
        addBook = (Button)findViewById(R.id.add_book);
        addBook.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
	        	new AuthenticatedRequestTask().execute(Network.svr_url);
				
			}
        });
        
        refreshBook = (Button)findViewById(R.id.booklist_refresh);
        refreshBook.setOnClickListener(new OnClickListener() {
        	@Override
        	public void onClick(View btn) {
        		new RefreshBookListTask().execute();
        	}
        });
        
        bookListStore = new ArrayList<Book>();
        bookList = (ListView)findViewById(R.id.booklist_books);
        bookListAdapter = new ArrayAdapter<Book>(this, R.layout.booklist_item, bookListStore);
        bookList.setAdapter(bookListAdapter);
	}
    private class AuthenticatedRequestTask extends AsyncTask<String, Object, HttpResponse> {
    	@Override
    	protected HttpResponse doInBackground(String... urls) {
    		try {
				idle.acquire();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		return Network.get(http_client, urls[0]);
    	}

    	protected void onPostExecute(HttpResponse result) {
    		try {
    			BufferedReader reader = new BufferedReader(new InputStreamReader(result.getEntity().getContent()));
    			String line = reader.readLine();
    			String newline;
    			while ((newline = reader.readLine()) != null) {
    				line += newline;
    			}
    			Toast.makeText(getApplicationContext(), line, Toast.LENGTH_LONG).show();                          
    		} catch (IllegalStateException e) {
    			e.printStackTrace();
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
    		idle.release();
    	}
    }
    private class RefreshBookListTask extends AsyncTask<String, Object, HttpResponse> {
    	@Override
    	protected HttpResponse doInBackground(String... urls) {
    		try {
				idle.acquire();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		return Network.get(http_client, Network.booklist_url);
    	}
    	
    	protected void onPostExecute(HttpResponse result) {
    		String sret = Network.readResponse(result);
    		if (sret == null) {
    			idle.release();
    			return;
    		}
    		
    		try {
    			Log.d("BookList", sret);
				JSONArray books = (JSONArray) new JSONTokener(sret).nextValue();
				bookListAdapter.clear();
				for (int i=0; i<books.length(); i++) {
					Book book = new Book((JSONObject)books.get(i));
					bookListAdapter.add(book);
				}
			} catch (JSONException e) {
				Log.d("BookListActivity", "Exception: " + e.toString());
				e.printStackTrace();
			}
    		idle.release();
    	}
    }
}
