package org.henryhu.blackbird;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import android.widget.Toast;

public class Network {
	static final String svr_domain = "blackbirdsvr.appspot.com";
	static final String svr_url = "https://" + svr_domain + "/";
	static final String booklist_url = svr_url + "book/list";
	static HttpResponse get(HttpClient http_client, String url) {
		try {
			HttpGet http_get = new HttpGet(url);
			return http_client.execute(http_get);
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	static String readResponse(HttpResponse result) {
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(result.getEntity().getContent()));
			StringBuilder ret = new StringBuilder();
			String newline;
			while ((newline = reader.readLine()) != null) {
				ret.append(newline);
				ret.append("\n");
			}
			return ret.toString();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
