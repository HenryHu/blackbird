package org.henryhu.blackbird;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.util.Base64;
import android.util.Log;

public class BookCache {
	String id;
	HttpClient http_client;
	Semaphore idle;

	final String prefix = "/sdcard/.blackbird/cache/";
	BookCache(HttpClient _http_client, String _id, Semaphore _idle) {
		http_client = _http_client;
		id = _id;
		idle = _idle;
	}
	
	byte[] getBlock(int block_num, boolean need_data) {
		Log.d("BookCache", String.format("getBlock(%d)", block_num));
		String filename = String.format(prefix + "%s/%d.tmp", id, block_num);
		new File(prefix + id).mkdirs();
		try {
			FileInputStream fis = new FileInputStream(filename);
			Log.d("BookCache", "found");
			if (need_data) {
				byte[] data = new byte[Network.block_size];
				fis.read(data);
				fis.close();
				return data;
			} else {
				return null;
			}
		} catch (Exception e) {
			if (e instanceof FileNotFoundException) {
				Log.d("BookCache", "not found. loading");
			} else {
				Log.e("BookCache", "error reading cache file: " + e.getMessage());
			}
			byte[] data = loadData(block_num * Network.block_size, (block_num + 1) * Network.block_size);
			try {
				FileOutputStream fos = new FileOutputStream(filename);
				fos.write(data);
				fos.close();
			} catch (IOException eio) {
				Log.e("BookCache", "Can't write cache file: " + eio.getMessage());
			}
			return data;
		}
	}
	
	public byte[] load(int start, int end, String reason) {
		byte[] result = new byte[end - start];
		int cur_pos = 0;
		Log.d("BookCache", String.format("load(%d, %d) for %s", start, end, reason));
		for (int i=start / Network.block_size; i<=end / Network.block_size; i++) {
			int block_start = i * Network.block_size;
			int block_end = (i+1) * Network.block_size;
			if (block_start >= end) break;
			byte[] block = getBlock(i, true);
			int start_pos, end_pos;
			if (block_start < start) {
				start_pos = start - block_start;
			} else {
				start_pos = 0;
			}
			if (block_end > end) {
				end_pos = end - block_start;
			} else {
				end_pos = block_end - block_start;
			}
			Log.d("BookCache", String.format("Block %d: start_pos %d end_pos %d", i, start_pos, end_pos));
			for (int j=start_pos; j<end_pos; j++) {
				result[cur_pos] = block[j];
				cur_pos++;
				if (j == block.length - 1) {
					// partial data
					break;
				}
			}
		}
		return result;
	}
	
	synchronized byte[] loadData(int start, int end){
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("id", id));
		params.add(new BasicNameValuePair("start", String.valueOf(start))); 
		params.add(new BasicNameValuePair("end", String.valueOf(end)));

		while (true) {
			try {
				idle.acquire();
				break;
			} catch (Exception e) {}
		}
		HttpResponse ret = Network.get(http_client, Network.bookget_url, params);

		String sret = Network.readResponse(ret);
		idle.release();
		if (sret == null) {
			Log.d("LoadBookTask", "No result");
			return null;
		}
		
		byte[] data = Base64.decode(sret, Base64.DEFAULT);
		return data;
	}

}
