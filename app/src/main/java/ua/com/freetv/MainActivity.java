package ua.com.freetv;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewTreeObserver;
import android.widget.GridView;
import android.widget.Toast;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;

import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;
import de.tavendo.autobahn.WebSocketHandler;


public class MainActivity extends Activity {

    private GridView gridView;
    private ImageAdapter imageAdapter;
    private int columnWidth, mPhotoSize, mPhotoSpacing;
    private ProgressDialog progressDialog;
    private String message;
    private int count;
    public  final WebSocketConnection mConnection = new WebSocketConnection();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        gridView = (GridView)findViewById(R.id.gridview);
        connectToServer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(checkNetworkConnection()){
            ChannelsWorker.reloadChannelBean();
            new AsyncTaskParser().execute();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.exit:
               // if (interstitial.isLoaded()) {
               //     interstitial.show();
               // }
                finish();
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public class AsyncTaskParser extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgressDoalog();
        }


        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            if(progressDialog != null && progressDialog.isShowing()){
                progressDialog.dismiss();
            }
            if(ChannelsWorker.getInstance().getCountChannelsBeans() == 0) {
                Toast.makeText(getApplicationContext(), getString(R.string.string_count_channels_is_zero), Toast.LENGTH_LONG).show();
                return;
            }
            if(!message.isEmpty()) Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();

            mPhotoSize = getResources().getDimensionPixelSize(R.dimen.photo_size);
            mPhotoSpacing = getResources().getDimensionPixelSize(R.dimen.photo_spacing);
            imageAdapter = new ImageAdapter(getApplicationContext());
            gridView.setAdapter(imageAdapter);
            gridView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (imageAdapter.getNumColumns() == 0) {
                        final int numColumns = (int) Math.floor(gridView.getWidth() / (mPhotoSize + mPhotoSpacing));
                        if (numColumns > 0) {
                            columnWidth = (gridView.getWidth() / numColumns) - mPhotoSpacing;
                            imageAdapter.setNumColumns(numColumns);
                            imageAdapter.setItemHeight(columnWidth);
                        }
                    }
                }
            });

        }

        @Override
        protected Void doInBackground(Void... voids) {
            StringBuffer stringBufferLocal = null;
            URL url;
            BufferedReader bufferedReader;
            String inputLine = null;
            JSONParser jsonParser;
            JSONObject jsonObject;
            JSONArray jsonArray;

            try {
                stringBufferLocal = new StringBuffer();
                url = new URL(getString(R.string.string_path_to_json_db));
                bufferedReader = new BufferedReader(new InputStreamReader(url.openStream()));
                while ((inputLine =bufferedReader.readLine()) != null){
                    stringBufferLocal.append(inputLine);
                }
                bufferedReader.close();
                jsonParser = new JSONParser();
                jsonObject = (JSONObject) jsonParser.parse(stringBufferLocal.toString());
                message = jsonObject.get("message").toString();
                count = Integer.parseInt(jsonObject.get("count").toString());
                for(int i=1; i<count; i++){
                    jsonArray = (JSONArray) jsonObject.get(String.valueOf(i));
                    ChannelsWorker.getInstance().addChannelBean(jsonArray.get(0).toString(), jsonArray.get(1).toString(), jsonArray.get(2).toString());
                    printToLogs(jsonArray.get(0).toString() + jsonArray.get(1).toString() + jsonArray.get(2).toString());
                }
                printToLogs(ChannelsWorker.getInstance().getChannelBean(1).getChannelName());
                printToLogs(String.valueOf(ChannelsWorker.getInstance().getCountChannelsBeans()) + " Count");
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), getString(R.string.string_count_channels_is_zero), Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
            return null;
        }
    }


    private void showProgressDoalog() {
        if(progressDialog == null) progressDialog = new ProgressDialog(this);
        progressDialog.setIndeterminate(true);
        progressDialog.setMessage(getResources().getString(R.string.string_dialog_message));
        progressDialog.setCancelable(true);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();
    }

    public boolean checkNetworkConnection() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnectedOrConnecting()) {
            return true;
        }
        return false;
    }

    void printToLogs (String string) {
        Log.d(getString(R.string.string_log_tag), string);
    }

    private void connectToServer() {
        try {
            if(!(mConnection.isConnected())){
                mConnection.connect(getString(R.string.server_path), new WebSocketHandler() {

                    @Override
                    public void onOpen() {}

                    @Override
                    public void onTextMessage(String message) {
                        if((message != null) && (message.equals(getString(R.string.string_show_advertisement)))){

                        }else {
                            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onClose(int code, String reason) {}
                });
            }
        } catch (WebSocketException e) {
            Toast.makeText(getApplicationContext(), "Произошла ошибка. Приносим свои извинения", Toast.LENGTH_SHORT).show();
            mConnection.sendTextMessage(getPrintStackTrace(e));
        }
    }

    public static String getPrintStackTrace(Exception e){
        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        e.printStackTrace(printWriter);
        return writer.toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mConnection != null) mConnection.disconnect();
    }
}
