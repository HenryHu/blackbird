package org.henryhu.blackbird;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;


public class BookListActivity extends Activity {
	SharedPreferences prefs;
	final int ACTIVITY_CHOOSE_BOOK = 1;
	final int DELETE_ID = 1;
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
				Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
				chooseFile.setType("file/*");
				Intent intent = Intent.createChooser(chooseFile, "Choose a book");
				startActivityForResult(intent, ACTIVITY_CHOOSE_BOOK);
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
        bookList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int pos,
					long id) {
				Book book = bookListStore.get((int) id);
				
				Intent intent = new Intent(getApplicationContext(), ReadBookActivity.class);
				intent.putExtra("book_id", book.id);
				intent.putExtra("book_title", book.title);
				intent.putExtra("book_size", book.size);
				startActivity(intent);
			}
        });
        bookList.setOnCreateContextMenuListener(this);
        
        new RefreshBookListTask().execute();
	}
	
	void showMsg(String msg) {
		Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG);
	}

    private class DeleteBookTask extends AsyncTask<Object, Object, HttpResponse> {
    	@Override
    	protected HttpResponse doInBackground(Object... args) {
    		try {
				idle.acquire();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		Book book = (Book)args[0];
    		List<NameValuePair> params = new ArrayList<NameValuePair>();
    		params.add(new BasicNameValuePair("id", book.id));
    		
    		return Network.post(http_client, Network.bookdel_url, params);
    	}
    	
    	protected void onPostExecute(HttpResponse result) {
    		String ret = Network.readResponse(result);
    		try {
				JSONObject retobj = (JSONObject)new JSONTokener(ret).nextValue();
				if (retobj.has("error")) {
					showMsg("Delete error: " + retobj.getString("error"));
				}
			} catch (JSONException e) {
				e.printStackTrace();
				showMsg("Fail to delete book");
			}
    		new RefreshBookListTask().execute();
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
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	switch(requestCode) {
    	case ACTIVITY_CHOOSE_BOOK: {
    		if (resultCode == RESULT_OK){
    			Uri uri = data.getData();
    			String filePath = uri.getPath();
    			Intent intent = new Intent(this, UploadBookActivity.class);
    			intent.putExtra("filepath", filePath);
    			startActivity(intent);
    		}
    	}
    	}
    }
    
    void deleteBook(long id) {
    	Book book = bookListStore.get((int)id);
    	new DeleteBookTask().execute(book);
    }
    public void onCreateContextMenu(ContextMenu menu, View v,  
    		ContextMenuInfo menuInfo) {  
    	super.onCreateContextMenu(menu, v, menuInfo);  
    	menu.add(0, DELETE_ID, 0,  "Delete");  
    }  
    public boolean onContextItemSelected(MenuItem item) {  
    	AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();  
    	switch (item.getItemId()) {  
    	case DELETE_ID:  
    		deleteBook(info.id);  
    		return true;  
    	default:  
    		return super.onContextItemSelected(item);  
    	}  
    }  
    
}
