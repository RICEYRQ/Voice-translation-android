package com.example.riceyrq.voicetranslation;

import android.Manifest;
import android.app.Activity;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class Main extends Activity {

    private final static String TAG = Main.class.getSimpleName();
    private Button requestPerm;
    private Button startAndStop;
    private Button play;
    private Button convert;
    private Spinner fromText;
    private Spinner toText;
    private TextView logView;
    private boolean inRecording = false;
    private AudioUtil audioUtil = AudioUtil.getmInstance();
    private MediaPlayer mediaPlayer = null;
    private String[] permissions = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
    };
    private List<String> mPermissionList = new ArrayList<>();
    private boolean inPlaying = false;
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == 0) {
                play.setText("播放");
                mediaPlayer = null;
                inPlaying = false;
            }
        }
    };

    private String s;
    private String[] texts = {
            "阿拉伯语",
            "中文",
            "英语",
            "法语",
            "德语",
            "意大利语",
            "日语",
            "葡萄牙语",
            "俄语",
            "西班牙语"
    };

    private String[] codes = {
         "ar", "zh", "en", "fr", "de", "it", "ja", "pt", "ru", "es"
    };

    private int fromPos = 1;
    private int toPos = 2;
    final static String key = "21f0c697b654459a83512409cccb4ee0";
    private static final String speechTranslateUriTemplate = "wss://dev.microsofttranslator.com/speech/translate?features=texttospeech&from=%1s&to=%2s&api-version=1.0";

    private ClipboardManager clipboard;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPerm = findViewById(R.id.requestPermission);
        startAndStop = findViewById(R.id.startAndStop);
        play = findViewById(R.id.play);
        convert = findViewById(R.id.convert);
        logView = findViewById(R.id.log);
        fromText = findViewById(R.id.from);
        toText = findViewById(R.id.to);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, texts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fromText.setAdapter(adapter);
        toText.setAdapter(adapter);
        fromText.setSelection(fromPos);
        toText.setSelection(toPos);

        clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        initBtn();
    }

    private void initBtn() {
        requestPerm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPermissionList.clear();
                for (int i = 0; i < permissions.length; i++) {
                    if (ContextCompat.checkSelfPermission(Main.this, permissions[i]) != PackageManager.PERMISSION_GRANTED) {
                        mPermissionList.add(permissions[i]);
                    }
                }
                if (mPermissionList.isEmpty()) {
                    showToast("权限都有了");
                    return;
                }
                String[] permissions = mPermissionList.toArray(new String[mPermissionList.size()]);//将List转为数组
                ActivityCompat.requestPermissions(Main.this, permissions, 2);
            }
        });
        startAndStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPermissionList.isEmpty()) {//未授予的权限为空，表示都授予了
                    Toast.makeText(Main.this,"已经授权", Toast.LENGTH_LONG).show();
                    if (!inRecording) {
                        audioUtil.startRecord();
                        startAndStop.setText("停止");
                        play.setEnabled(false);
                    } else {
                        audioUtil.stopRecord();
                        startAndStop.setText("开始");

                        play.setEnabled(true);
                    }
                    inRecording = !inRecording;
                } else {//请求权限方法
                    return;
                }
            }
        });
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                    play.setText("播放");
                    return;
                }
                mediaPlayer = new MediaPlayer();
                try {
                    mediaPlayer.reset();
                    mediaPlayer.setDataSource(AudioUtil.inFileName);
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                    play.setText("停止");
                    new Thread(new SeekBarThread()).start();
                } catch (IOException ignored) {

                }


            }
        });
        play.setVisibility(View.GONE);
        convert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logView.setText("");
                getTaken();

            }
        });
        fromText.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                fromPos = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                fromPos = 1;
            }
        });
        toText.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                toPos = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                toPos = 2;
            }
        });
    }


    class SeekBarThread implements Runnable {

        @Override
        public void run() {
            while (mediaPlayer != null && !inPlaying) {
                // 将SeekBar位置设置到当前播放位置
                if (!mediaPlayer.isPlaying()){
                    handler.sendEmptyMessage(0);
                }
                try {
                    // 每100毫秒更新一次位置
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        if (requestCode == 1) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showToast("权限已申请");
            } else {
                showToast("权限已拒绝");
            }
        }else if (requestCode == 2){
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    //判断是否勾选禁止后不再询问
                    boolean showRequestPermission = ActivityCompat.shouldShowRequestPermissionRationale(Main.this, permissions[i]);
                    if (showRequestPermission) {
                        showToast("权限未申请");
                    }
                } else {
                    mPermissionList.remove(permissions[i]);
                }
            }
            if (mPermissionList.isEmpty()) {

            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }



    private void showToast(String string){
        Toast.makeText(Main.this,string,Toast.LENGTH_LONG).show();
    }

    private static String generateWsUrl(String from, String to) {
        return String.format(speechTranslateUriTemplate, from, to);
    }

    private void postTranslate(final String token) {
        final String from = codes[fromPos];
        String to = codes[toPos];
        String traceId = UUID.randomUUID().toString();
        final File file = new File(AudioUtil.inFileName);
        final int count = 0;
        WebSocketListener listener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.e(TAG, "onOpen ");
                final byte[] silenceBytes = new byte[3200];
                byte[] bytes = new byte[(int) file.length()];
                Log.e(TAG, "file length " + file.length());
                Log.e(TAG, "file exist " + file.exists());
                int counter = 0;
                FileInputStream fileInputStream = null;
                try {
                    fileInputStream = new FileInputStream(file);
                } catch (FileNotFoundException e) {
                    Log.e(TAG, String.valueOf(e));
                }
               // try {
                    Log.e(TAG, "to send");
                    boolean isOk = webSocket.send(ByteString.of(ByteBuffer.wrap(AudioUtil.getHeader())));
                    if (!isOk) {
                        Log.e(TAG, "send error");
                    } else {
                        Log.e(TAG, "send ok");
                    }
                try {
                    BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
                    bufferedInputStream.read(bytes);
                    isOk = webSocket.send(ByteString.of(ByteBuffer.wrap(bytes)));
                    if (!isOk) {
                        Log.e(TAG, "send error");
                    } else {
                        Log.e(TAG, "send ok");
                    }
                    for (int i = 0; i < 10; i++) {
                        isOk = webSocket.send(ByteString.of(ByteBuffer.wrap(silenceBytes)));
                        if (!isOk) {
                            Log.e(TAG, "send error " + i);
                        } else {
                            Log.e(TAG, "send ok " + i);
                        }
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Log.e(TAG, String.valueOf(e));
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, String.valueOf(e));
                }

            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JSONObject j = new JSONObject(text);
                    final String translation = j.getString("translation");
                    Main.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            logView.setText(translation);
                        }
                    });
                } catch (JSONException e) {
                    Log.e(TAG, text);
                }
                Log.e(TAG, "onMessage " + text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                Log.e(TAG, "onMessage " + bytes.toByteArray().toString());
                try {
                    File tempMp3 = File.createTempFile("kurchina", "mp3", getCacheDir());
                    tempMp3.deleteOnExit();
                    FileOutputStream fos = new FileOutputStream(tempMp3);
                    fos.write(bytes.toByteArray());
                    fos.close();
                    mediaPlayer = new MediaPlayer();
                    mediaPlayer.reset();
                    FileInputStream fis = new FileInputStream(tempMp3);
                    mediaPlayer.setDataSource(fis.getFD());
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                    new Thread(new SeekBarThread()).start();
                } catch (IOException e) {
                    Log.e(TAG, String.valueOf(e));
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.e(TAG, "onClosed " + reason);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.e(TAG, "onClosing " + reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "onError " + String.valueOf(t));
            }
        };
        Request request = new Request.Builder()
                .url(generateWsUrl(from, to))
                .header("Authorization", "Bearer " + token)
                .header("X-ClientTraceId", traceId)
                .build();
        OkHttpClient client = new OkHttpClient();
        WebSocket webSocket = client.newWebSocket(request, listener);

    }

    private void getTaken() {
        new Thread(new Runnable() {
            @Override
            public void run() {
//                String url = "https://api.cognitive.microsoft.com/sts/v1.0/issueToken";
                OkHttpClient okHttpClient = new OkHttpClient.Builder()
                        .addInterceptor(new LogInterceptor())
                        .build();
                FormBody formBody = new FormBody.Builder()
                        .add("data", "")
                        .build();
                Request request = new Request.Builder()
                        .url("https://api.cognitive.microsoft.com/sts/v1.0/issueToken")
                        .header("Ocp-Apim-Subscription-Key", "21f0c697b654459a83512409cccb4ee0")
                        .post(formBody)
                        .build();
                Call call = okHttpClient.newCall(request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "get onFailure " + e);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {

                            }
                        });
                    }

                    @Override
                    public void onResponse(Call call, final Response response) throws IOException {

                        s = response.body().string();
                        Log.e(TAG, "get onResponse " + s);
                        if (s.contains("statusCode")) {
                            Main.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    logView.setText(s);
                                }
                            });
                            return;
                        } else {
                            postTranslate(s);
                        }
                    }
                });
            }
        }).start();
    }

    private class LogInterceptor implements Interceptor {

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Log.e(TAG, "request:" + request.toString());
            Log.e(TAG, "header:" + request.headers().toString());
            long t1 = System.nanoTime();
            okhttp3.Response response = chain.proceed(chain.request());
            long t2 = System.nanoTime();
            Log.e(TAG, String.format(Locale.getDefault(), "Received response for %s in %.1fms%n%s",
                    response.request().url(), (t2 - t1) / 1e6d, response.headers()));
            okhttp3.MediaType mediaType = response.body().contentType();
            String content = response.body().string();
            Log.e(TAG, "response body:" + content);
            return response.newBuilder()
                    .body(okhttp3.ResponseBody.create(mediaType, content))
                    .build();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null)
            mediaPlayer.release();
    }
}
