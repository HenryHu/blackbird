package org.henryhu.blackbird;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class LoginActivity extends Activity {
    /** Called when the activity is first created. */
	
	SharedPreferences prefs;
	Button loginbtn = null;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);

        loginbtn = (Button)findViewById(R.id.login_btn);
        loginbtn.setOnClickListener(new OnClickListener() {

        	@Override
        	public void onClick(View arg0) {
        		Intent intent = new Intent(getApplicationContext(), AccountList.class);
        		startActivity(intent);
        		finish();
        	}
        });
        
        prefs = getSharedPreferences("data", 0);
/*        String cookie = prefs.getString("SACSID", "");
        if (!cookie.equals("")) {
        	Intent intent = new Intent(this, BookListActivity.class);
        	startActivity(intent);
        	finish();
        }*/
    }
    
    
}