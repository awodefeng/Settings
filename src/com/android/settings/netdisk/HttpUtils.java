package com.android.settings.netdisk;

import android.content.Context;
import android.net.http.AndroidHttpClient;
import android.util.Log;

import com.android.internal.http.multipart.FilePart;
import com.android.internal.http.multipart.MultipartEntity;
import com.android.internal.http.multipart.Part;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class HttpUtils {
    private static final String TAG = HttpUtils.class.getSimpleName();

    public static String httpGet(String url) {

        try {
            URL requestUrl = new URL(url);
            HttpsURLConnection httpsURLConnection = (HttpsURLConnection) requestUrl.openConnection();
            httpsURLConnection.setConnectTimeout(30 * 1000);
            httpsURLConnection.setReadTimeout(30 * 1000);
            httpsURLConnection.setRequestMethod("GET");
            httpsURLConnection.setRequestProperty("Charset", "UTF-8");
            httpsURLConnection.setRequestProperty("User-Agent", Constants.USER_AGENT);

            if (httpsURLConnection.getResponseCode() == HttpsURLConnection.HTTP_OK) {
                String result = readDataFromStream(httpsURLConnection.getInputStream());
                Log.e(TAG, "com.android.settings.netdisk "  + "httpGet result = " + result);
                return result;
            } else {
                String error = readDataFromStream(httpsURLConnection.getErrorStream());
                Log.e(TAG, "error = " + error);
                return error;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static String httpPost(String url, String data, String contentType) {
        try {
            URL requestUrl = new URL(url);
            HttpsURLConnection httpsURLConnection = (HttpsURLConnection) requestUrl.openConnection();

            httpsURLConnection.setConnectTimeout(30 * 1000);
            httpsURLConnection.setReadTimeout(30 * 1000);

            httpsURLConnection.setRequestMethod("POST");
            httpsURLConnection.setRequestProperty("Content-Type", contentType);
            httpsURLConnection.setRequestProperty("Charset", "UTF-8");
            httpsURLConnection.setRequestProperty("User-Agent", Constants.USER_AGENT);
            httpsURLConnection.setUseCaches(false);
            httpsURLConnection.setDoOutput(true);

            byte[] message = data.getBytes("UTF-8");

            httpsURLConnection.setRequestProperty("Content-Length", "" + message.length);
            OutputStreamWriter out = new OutputStreamWriter(httpsURLConnection.getOutputStream(), "UTF-8");
            out.write(data);
            out.flush();
            out.close();

            if (httpsURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String result = readDataFromStream(httpsURLConnection.getInputStream());
                Log.e(TAG, "httpPost result = " + result);
                return result;

            } else {
                String error = readDataFromStream(httpsURLConnection.getErrorStream());
                Log.e(TAG, "httpPost error = " + error);
                return error;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static String httpPostFile(Context context, String url, File file) {
        AndroidHttpClient httpClient = AndroidHttpClient.newInstance(Constants.USER_AGENT, context);
        try {
            HttpPost httpPost = new HttpPost(url);

            FilePart filePart = new FilePart("file", file);
            Part[] parts = {filePart};
            MultipartEntity multipartEntity = new MultipartEntity(parts);
            httpPost.setEntity(multipartEntity);

            HttpResponse response = httpClient.execute(httpPost);
            if (response.getStatusLine().getStatusCode() == 200) {
                String result = EntityUtils.toString(response.getEntity(), "UTF-8");
                Log.e(TAG, "result: " + result);
                return result;
            } else {
                String error = EntityUtils.toString(response.getEntity(), "UTF-8");
                Log.e(TAG, "error: " + error);
                return error;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            httpClient.close();
        }
    }

    public static String readDataFromStream(InputStream inputStream) {
        try {
            InputStream is = new BufferedInputStream(inputStream);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int readLen;
            while ((readLen = is.read(buf)) != -1) {
                baos.write(buf, 0, readLen);
            }
            return new String(baos.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }
}
