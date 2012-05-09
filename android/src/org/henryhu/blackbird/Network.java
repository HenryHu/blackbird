package org.henryhu.blackbird;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.params.HttpParams;

import android.util.Log;
import android.widget.Toast;

public class Network {
	static final String svr_domain = "blackbirdsvr.appspot.com";
	static final String svr_url = "https://" + svr_domain + "/";
	static final String booklist_url = svr_url + "book/list";
	static final String bookadd_url = svr_url + "book/add";
	static final String bookget_url = svr_url + "book/get";
	static final String bookput_url = svr_url + "book/put";
	static final String bookdel_url = svr_url + "book/del";
    static final String bookhere_url = svr_url + "book/here";
	static final int block_size = 4096;
	static HttpResponse post(HttpClient http_client, String url, List<NameValuePair> params) {
		try {
			HttpPost http_post = new HttpPost(url);
			http_post.setEntity(new UrlEncodedFormEntity(params));
			return http_client.execute(http_post);
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	static HttpResponse get(HttpClient http_client, String url, List<NameValuePair> params) {
		String param = URLEncodedUtils.format(params, "utf-8");
		String purl = url + "?" + param;
		return get(http_client, purl);
	}
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
			Log.d("Network", "response: " + ret);
			return ret.toString();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
