package org.henryhu.blackbird;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class AuthActivity extends Activity {
    DefaultHttpClient http_client = new DefaultHttpClient();
    TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.authenticate);
            
            status = (TextView)findViewById(R.id.authStatus);
    }
    
    void setStatus(String _status) {
    	status.setText(_status);
    }

    @Override
    protected void onResume() {
            super.onResume();
            Intent intent = getIntent();
            AccountManager accountManager = AccountManager.get(getApplicationContext());
            Account account = (Account)intent.getExtras().get("account");
            accountManager.getAuthToken(account, "ah", false, new GetAuthTokenCallback(), null);
    }

    private class GetAuthTokenCallback implements AccountManagerCallback<Bundle> {
    	public void run(AccountManagerFuture<Bundle> result) {
    		Bundle bundle;
    		try {
    			bundle = result.getResult();
    			Intent intent = (Intent)bundle.get(AccountManager.KEY_INTENT);
    			if(intent != null) {
    				// User input required
    				startActivity(intent);
    			} else {
    				onGetAuthToken(bundle);
    			}
    		} catch (OperationCanceledException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (AuthenticatorException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}
    };

    protected void onGetAuthToken(Bundle bundle) {
    	String auth_token = bundle.getString(AccountManager.KEY_AUTHTOKEN);
        new GetCookieTask().execute(auth_token);
    }

    private class GetCookieTask extends AsyncTask<String, Object, Boolean> {
    	protected Boolean doInBackground(String... tokens) {
    		try {
    			// Don't follow redirects
    			http_client.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);

    			HttpGet http_get = new HttpGet(Network.svr_url + "_ah/login?continue=http://localhost/&auth=" + tokens[0]);
    			HttpResponse response;
    			response = http_client.execute(http_get);
    			if(response.getStatusLine().getStatusCode() != 302) {
    				// Response should be a redirect
    				Log.d("auther", "fail with error: " + response.getStatusLine().toString());
    				return false;
    			}

    			for(Cookie cookie : http_client.getCookieStore().getCookies()) {
    				if(cookie.getName().equals("SACSID"))
    					return true;
    			}
    		} catch (ClientProtocolException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} finally {
    			http_client.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, true);
    		}
    		return false;
    	}

    	protected void onPostExecute(Boolean result) {
    		if (result) {
    			for (Cookie cookie : http_client.getCookieStore().getCookies()) {
    				if (cookie.getName().equals("SACSID")) {
    					Editor edit = getSharedPreferences("data", 0).edit();
    					edit.putString("SACSID", cookie.getValue());
    					edit.commit();
    				}
    			}
    			Intent intent = new Intent(getApplicationContext(), BookListActivity.class);
    			startActivity(intent);
    			finish();
    		}
    		else {
    			setStatus("Authentication fail!");
    			Toast.makeText(getApplicationContext(), "Authentication fail!", Toast.LENGTH_LONG).show();
    		}
    	}
    }


}