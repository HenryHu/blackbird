package org.henryhu.blackbird;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
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
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class UploadBookActivity extends Activity {
	Semaphore idle = new Semaphore(1);
	EditText title; 
	TextView filepath;
	DefaultHttpClient http_client = new DefaultHttpClient();
	SharedPreferences prefs;
	Button btnOk, btnCancel;
	TextView status;
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.upload_book);
		
		title = (EditText)findViewById(R.id.upload_title);
		filepath = (TextView)findViewById(R.id.upload_filepath);
		status = (TextView)findViewById(R.id.upload_status);
		
		filepath.setText(getIntent().getExtras().getString("filepath"));
		
		prefs = getSharedPreferences("data", 0);
        BasicClientCookie cookie = new BasicClientCookie("SACSID", prefs.getString("SACSID", ""));
        cookie.setSecure(true);
        cookie.setDomain(Network.svr_domain);
        cookie.setPath("/");
        
        http_client.getCookieStore().addCookie(cookie);
        
        btnOk = (Button)findViewById(R.id.upload_ok);
        btnCancel = (Button)findViewById(R.id.upload_cancel);
        
        btnOk.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				new AddBookTask().execute(title.getText().toString(), filepath.getText().toString());
			}
        });
        btnCancel.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				finish();
			}
        });

	}
	// arg: book name, book size
    private class AddBookTask extends AsyncTask<Object, Object, String> {
    	String book_path;
    	int book_size;
    	String statusline;
    	
    	@Override
    	protected String doInBackground(Object... args) {
    		String book_title = (String)args[0];
    		book_path = (String)args[1];
    		try {
				idle.acquire();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		File file = new File(book_path);
    		book_size = (int) file.length();
    		
    		List<NameValuePair> params = new ArrayList<NameValuePair>();
    		params.add(new BasicNameValuePair("title", book_title));
    		params.add(new BasicNameValuePair("size", String.valueOf(book_size)));
    		
    		HttpResponse result = Network.post(http_client, Network.bookadd_url, params);
			String ret = Network.readResponse(result);
			statusline = result.getStatusLine().toString();
			return ret;
    	}
    	
    	protected void onPreExecute() {
    		setStatus("uploading book....");
    	}

    	protected void onPostExecute(String ret) {
    		JSONObject retobj = null;
    		try {
    			retobj = (JSONObject) new JSONTokener(ret).nextValue();
    			String book_id = retobj.getString("id");
    			setStatus("uploading book data....");
    			
    			new UploadBookDataTask().execute(book_path, book_id, 0, book_size);
    		} catch (IllegalStateException e) {
    			e.printStackTrace();
				setStatus("Fail to upload book: " + statusline);
    		} catch (JSONException e) {
				e.printStackTrace();
				if (retobj == null)
					setStatus("Fail to upload book: " + statusline);
				else
					try {
						setStatus("Fail to upload: " + retobj.getString("error"));
					} catch (JSONException e1) {
						e1.printStackTrace();
						setStatus("Fail to upload book: " + statusline);
					}
			} catch (Exception e) {
				setStatus("Fali to upload book");
			}
    		idle.release();
    	}
    }
    private class UploadBookDataTask extends AsyncTask<Object, Object, HttpResponse> {
		String book_path;
		String book_id;
		int cur_pos;
		int book_size;
		int read_len = 0;
    	@Override
    	protected HttpResponse doInBackground(Object... args) {
    		book_path = (String)args[0];
    		book_id = (String)args[1];
    		cur_pos = (Integer)args[2];
    		book_size = (Integer)args[3];
    		try {
				idle.acquire();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		
    		File file = new File(book_path);
    		byte []data = new byte[Network.block_size];
    		InputStream inputStream = null;
    		try {
    			inputStream = new FileInputStream(book_path);
    			inputStream.skip(cur_pos);
    			read_len = inputStream.read(data);
    		} catch (FileNotFoundException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}

    		String book_data = Base64.encodeToString(data, Base64.DEFAULT);
    		
    		List<NameValuePair> params = new ArrayList<NameValuePair>();
    		params.add(new BasicNameValuePair("id", book_id));
    		params.add(new BasicNameValuePair("data", book_data));
    		params.add(new BasicNameValuePair("start", String.valueOf(cur_pos)));
    		params.add(new BasicNameValuePair("end", String.valueOf(cur_pos + read_len)));
    		
    		return Network.post(http_client, Network.bookput_url, params);
    	}
    	
    	protected void onPostExecute(HttpResponse result) {
    		try {
    			setStatus(String.format("uploading book data, %d / %d....", cur_pos + read_len, book_size));
    			
    			if (read_len == Network.block_size)
    				new UploadBookDataTask().execute(book_path, book_id, cur_pos + read_len, book_size);
    			else
    				finish();
    		} catch (IllegalStateException e) {
    			e.printStackTrace();
    			setStatus("Fail to upload: " + result.getStatusLine().toString());
    		}
    		idle.release();
    	}
    }

    void setStatus(String _status) {
    	status.setText(_status);
    }
}
