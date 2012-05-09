package org.henryhu.blackbird;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;


public class ReadBookActivity extends Activity {
	String book_id;
	String book_title;
	int book_size;
    int book_place;
	TextView book_text;
	Button next, prev, jumpto;
	int cur_pos;
    DefaultHttpClient http_client = new DefaultHttpClient();
	SharedPreferences prefs;
	ScrollView book_scroll;
	int page_size = 1024;
    int sync_place_limit = 10240;
    BookCache bookCache;

	Semaphore idle = new Semaphore(1);
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.read_book);
        prefs = getSharedPreferences("data", 0);

		book_id = getIntent().getExtras().getString("book_id");
		book_title = getIntent().getExtras().getString("book_title");
		book_size = getIntent().getExtras().getInt("book_size");
        book_place = getIntent().getExtras().getInt("book_place");
		
		book_text = (TextView)findViewById(R.id.read_text);
		next = (Button)findViewById(R.id.read_next);
		prev = (Button)findViewById(R.id.read_prev);
		jumpto = (Button)findViewById(R.id.read_jump);
		book_scroll = (ScrollView)findViewById(R.id.read_scroll);
		
		int height = getWindowManager().getDefaultDisplay().getHeight() - 50;
		int width = getWindowManager().getDefaultDisplay().getWidth();
		Log.d("ReadBook", String.format("height: %d line: %d", 
				height, book_text.getLineHeight()));
		page_size = height * width / book_text.getLineHeight() / 25;
//		page_size = (page_size + 1023) / 1024 * 1024;
		Log.d("ReadBook", "Page size: " + String.valueOf(page_size));
		
		BasicClientCookie cookie = new BasicClientCookie("SACSID", prefs.getString("SACSID", ""));
        cookie.setSecure(true);
        cookie.setDomain(Network.svr_domain);
        cookie.setPath("/");
        
        http_client.getCookieStore().addCookie(cookie);
        
        bookCache = new BookCache(http_client, book_id, idle);
		
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
				jumpDialog();
			}
		});
		
        cur_pos = book_place;
		loadPage();
	}
	
	void loadPage() {
//		new LoadBookTask().execute(new Book(book_title, book_size, book_id, book_place), cur_pos, cur_pos + page_size);
		int last_pos;
		if (cur_pos + page_size > book_size)
			last_pos = book_size;
		else
			last_pos = cur_pos + page_size;
		Log.d("ReadBook", String.format("load: %d - %d", cur_pos, last_pos));
		new LoadBookCachedTask().execute(book_id, cur_pos, last_pos, true);
	}
	
	void prefetchPage(int start_pos) {
		if (start_pos >= book_size) return;
		int last_pos;
		if (start_pos + 3 * page_size > book_size)
			last_pos = book_size;
		else
			last_pos = start_pos + 3 * page_size;
		Log.d("ReadBook", String.format("prefetch: %d - %d", start_pos, last_pos));
		new LoadBookCachedTask().execute(book_id, start_pos, last_pos, false);
	}
	
	void nextPage() {
		if (cur_pos + page_size >= book_size)
			return;
		cur_pos += page_size;
        if (Math.abs(cur_pos - book_place) > sync_place_limit) {
            syncPlace();
        }
		loadPage();
		prefetchPage(cur_pos + page_size);
	}
	
	void prevPage() {
		cur_pos -= page_size;
		if (cur_pos < 0)
			cur_pos = 0;
        if (Math.abs(cur_pos - book_place) > sync_place_limit) {
            syncPlace();
        }
		loadPage();
	}

    void syncPlace() {
        new SyncPlaceTask().execute(book_id, cur_pos);
    }

    private class SyncPlaceTask extends AsyncTask<Object, Object, String> {
        int my_pos;
        String book_id;

        @Override
        protected String doInBackground(Object... args) {
            boolean acquired = false;
            while (!acquired) {
                try {
                    idle.acquire();
                    acquired = true;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            book_id = (String)args[0];
            my_pos = (Integer)args[1];

            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("id", book_id));
            params.add(new BasicNameValuePair("place", String.valueOf(my_pos)));

            HttpResponse result = Network.post(http_client, Network.bookhere_url, params);
            String sret = Network.readResponse(result);
            return sret;
        }

        protected void onPostExecute(String sret) {
            Log.d("SyncPlaceTask", "PostExecute()");

            if (sret == null) {
                Log.d("SyncPlaceTask", "error: no result");
                idle.release();
                return;
            }

            try {
                JSONObject obj = (JSONObject)new JSONTokener(sret).nextValue();
                if (obj.has("error")) {
                    Log.d("SyncPlaceTask", "error: " + obj.getString("error"));
                }
            } catch (JSONException e) {
                Log.d("SyncPlaceTask", "error: exception " + e.getMessage());
                idle.release();
                return;
            }

            book_place = my_pos;
            showMsg("Reading progress saved.");
            idle.release();
        }
    }
    
    private class LoadBookCachedTask extends AsyncTask<Object, Object, byte[]> {
		int start, end;
		String book_id;
		boolean display;
    	@Override
    	protected byte[] doInBackground(Object... args) {
    		book_id = (String)args[0];
    		start = (Integer)args[1];
    		end = (Integer)args[2];
    		display = (Boolean)args[3];
    		
    		byte[] data = bookCache.load(start, end, display ? "load" : "prefetch");
    		return data;
    	}
    	
    	protected void onPostExecute(byte[] result) {
    		if (!display) return;
    		if (result == null) {
    			showMsg("fail to read book!");
    			return;
    		}
    		String ret;
    		int len = result.length;
    		if ((ret = tryDecode(result, "gbk", 0, len)) == null) {
    			if ((ret = tryDecode(result, "gbk", 1, len)) == null) {
    				if ((ret = tryDecode(result, "gbk", 1, len - 1)) == null) {
    					if ((ret = tryDecode(result, "gbk", 0, len - 1)) == null) {
    						// no way! illegal file
    						try {
    							ret = new String(result, "gbk");
    						} catch (Exception e) {}
    					} else {
    						// add last char
    						new LoadBookCachedTask().execute(book_id, start, end + 1, display);
    						return;
    					}
    				} else {
    					// add last char
    					new LoadBookCachedTask().execute(book_id, start, end + 1, display);
    					return;
    				}
    			} else {
    				// ret is OK now
    				// cut 1st char is ok
    			}
    		} else {
    			// completely ok
    		}
    		book_text.setText(ret);
			book_scroll.scrollTo(0, 0);
			cur_pos = start;
    	}
    }
    
    String tryDecode(byte[] data, String encoding, int start, int end) {
    	Charset cset = Charset.forName(encoding);
    	CharsetDecoder dec = cset.newDecoder();
    	ByteBuffer buf = ByteBuffer.wrap(data, start, end - start);
    	CharBuffer cbuf = null;
    	try {
    		cbuf = dec.decode(buf);
    	} catch (CharacterCodingException e) {
    		return null;
    	}
    	return cbuf.toString();
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
				book_scroll.scrollTo(0, 0);
			} catch (UnsupportedEncodingException e) { }
   			cur_pos = start;
   			
   			
    		idle.release();
    	}
    }
	
	protected void onPause() {
		super.onPause();
		syncPlace();
	}
	
	void showMsg(String msg) {
		Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG);
	}
	
	protected void jumpDialog() {
		AlertDialog.Builder builder = new Builder(this);
		builder.setMessage("Jump to?");
		builder.setTitle("Question");
		final SeekBar seekbar = new SeekBar(this);
		seekbar.setMax(book_size);
		seekbar.setProgress(cur_pos);
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				int selection = seekbar.getProgress();
				selection = selection / page_size * page_size;
				cur_pos = selection;
				loadPage();
		}
		});
		builder.setView(seekbar);
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// TODO Auto-generated method stub
				dialog.dismiss();
				
			}
		});
		builder.create().show();
	}
}
