package com.huawei.hms.audioeditor.demo;

import android.app.Dialog;
import android.content.Context;
import android.media.AudioFormat;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.huawei.hms.audioeditor.common.agc.HAEApplication;
import com.huawei.hms.audioeditor.common.network.NetworkUtil;
import com.huawei.hms.audioeditor.demo.util.FileUtils;
import com.huawei.hms.audioeditor.demo.util.PCMToWav;
import com.huawei.hms.audioeditor.sdk.engine.dubbing.HAEAiDubbingAudioInfo;
import com.huawei.hms.audioeditor.sdk.engine.dubbing.HAEAiDubbingCallback;
import com.huawei.hms.audioeditor.sdk.engine.dubbing.HAEAiDubbingConfig;
import com.huawei.hms.audioeditor.sdk.engine.dubbing.HAEAiDubbingConstants;
import com.huawei.hms.audioeditor.sdk.engine.dubbing.HAEAiDubbingEngine;
import com.huawei.hms.audioeditor.sdk.engine.dubbing.HAEAiDubbingError;
import com.huawei.hms.audioeditor.sdk.engine.dubbing.HAEAiDubbingSpeaker;
import com.huawei.hms.audioeditor.sdk.engine.dubbing.HAEAiDubbingWarn;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * AI dubbing
 * @date 2021/8/10
 * @since 2021/8/10
 */
public class AiDubbingAudioActivity extends AppCompatActivity
        implements View.OnClickListener,
                SeekBar.OnSeekBarChangeListener,
                AiDubbingLanguageAdapter.OnItemClickListener,
                AiDubbingStyleAdapter.OnItemClickListener {
    private static final String TAG = "AiDubbingAudioActivity";

    public static String AUDIO_PATH;

    private EditText editText;

    private Button addBtn;
    private Button pauseBtn;
    private Button playBtn;
    private Button stopBtn;

    private SeekBar speedSeek;
    private SeekBar volumeSeek;

    private TextView textView_volume;
    private TextView textView_speed;
    private TextView languageText;
    private TextView speakerText;
    private TextView modeText;

    private ImageView clear;

    private RelativeLayout rl_language;
    private RelativeLayout rl_speaker;
    private RelativeLayout rl_mode;

    private HAEAiDubbingEngine mEngine;

    private Map<String, String> languageMap = new HashMap<>();
    private int[] playModeResources = new int[] {R.string.queuing_mode, R.string.clear_mode};
    private boolean isFlush = false; // Sequence or queued playback.
    private boolean isPause = false;

    private int speedVal = 100;
    private int volumeVal = 120;

    private Map<String, String> temp = new HashMap<>();

    private static final String AI_DUBBING_PATH = "aiDubbing";
    private static final String PCM_EXT = ".pcm";
    private static final String WAV_EXT = ".wav";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dubbing_audio);
        // Setting the APIkey of the SDK
        HAEApplication.getInstance().setApiKey("Set your APIKey");
        HAEAiDubbingConfig mlConfigs = new HAEAiDubbingConfig();
        mEngine = new HAEAiDubbingEngine(mlConfigs);
        AUDIO_PATH = FileUtils.initFile(this);
        initView();
        initAction();
    }

    private void initView() {
        editText = findViewById(R.id.edit_text);
        addBtn = findViewById(R.id.btn_add);

        playBtn = findViewById(R.id.btn_play);
        pauseBtn = findViewById(R.id.btn_pause);
        stopBtn = findViewById(R.id.btn_stop);
        volumeSeek = findViewById(R.id.volumeSeek);
        speedSeek = findViewById(R.id.speedSeek);
        volumeSeek.setMax(150);
        speedSeek.setMax(150);

        clear = findViewById(R.id.close);

        textView_volume = findViewById(R.id.textView_volume);
        textView_speed = findViewById(R.id.textView_speed);

        languageText = findViewById(R.id.languagetext);
        speakerText = findViewById(R.id.styletext);
        modeText = findViewById(R.id.modetext);
        rl_language = findViewById(R.id.rl_language);
        rl_speaker = findViewById(R.id.rl_style);
        rl_mode = findViewById(R.id.rl_mode);

        initSpeakerList();
        createModeDialog();

        textView_volume.setText(120 + "");
        textView_speed.setText(100 + "");
    }

    HAEAiDubbingCallback callback =
            new HAEAiDubbingCallback() {
                @Override
                public void onError(String taskId, HAEAiDubbingError err) {
                    stopAiDubbing();
                    errToast(err);
                }

                @Override
                public void onWarn(String taskId, HAEAiDubbingWarn warn) {}

                @Override
                public void onRangeStart(String taskId, int start, int end) {}

                @Override
                public void onAudioAvailable(
                        String taskId,
                        HAEAiDubbingAudioInfo HAEAiDubbingAudioInfo,
                        int i,
                        Pair<Integer, Integer> pair,
                        Bundle bundle) {
                    // Start to receive files and save them as files.
                    String pcmFile = getAudioFileNameByTask(taskId, PCM_EXT);
                    FileUtils.writeBufferToFile(HAEAiDubbingAudioInfo.getAudioData(), pcmFile, true);
                }

                @Override
                public void onEvent(String taskId, int eventID, Bundle bundle) {
                    // The synthesis is complete.
                    if (eventID == HAEAiDubbingConstants.EVENT_SYNTHESIS_COMPLETE) {
                        String pcmFile = getAudioFileNameByTask(taskId, PCM_EXT);
                        String waveFile = getAudioFileNameByTask(taskId, WAV_EXT);
                        final String convertWaveFile =
                                PCMToWav.convertWaveFile(
                                        pcmFile,
                                        waveFile,
                                        16000,
                                        AudioFormat.CHANNEL_IN_MONO,
                                        AudioFormat.ENCODING_PCM_16BIT);
                        if (isSpeechNoPreview) {
                            runOnUiThread(
                                    () -> {
                                        addBtn.setText(R.string.queue_add);
                                        Toast.makeText(AiDubbingAudioActivity.this, convertWaveFile, Toast.LENGTH_SHORT)
                                                .show();
                                    });
                            stopAiDubbing();
                        }
                    }
                }

                @Override
                public void onSpeakerUpdate(List<HAEAiDubbingSpeaker> speakerList, List<String> lanList, List<String> lanDescList) {
                    notifySpeaker(speakerList,lanList,lanDescList);
                }
            };

    private void notifySpeaker(List<HAEAiDubbingSpeaker> speakerList, List<String> lanList, List<String> lanDescList) {
        runOnUiThread(
                () -> {
                    if(aiDubbingStyleAdapter != null && lanDescList != null){
                        languageCodeList = lanList;
                        languageDescList = lanDescList;
                        if (aiDubbingLanguageAdapter != null){
                            aiDubbingLanguageAdapter.setList(languageDescList);
                        }
                    }
                    // Update the data obtained from the network.
                    if (aiDubbingStyleAdapter != null) {
                        speakerCodeList.clear();
                        speakerTypeList.clear();
                        List<TextToSpeechStyleBean> divideBeanList = generateList(speakerList);
                        for (TextToSpeechStyleBean divideBean : divideBeanList) {
                            speakerCodeList.add(divideBean.getSpeaker().getSpeakerDesc());
                            speakerTypeList.add(divideBean.getSpeaker().getName());
                        }
                        aiDubbingStyleAdapter.setList(speakerCodeList);
                    }
                });
    }

    private void errToast(HAEAiDubbingError err) {
        runOnUiThread(
                () -> {
                    String msg = "";
                    switch (err.getErrorId()) {
                        case HAEAiDubbingError.ERR_ILLEGAL_PARAMETER:
                            msg = AiDubbingAudioActivity.this.getResources()
                                    .getString(R.string.text_to_audio_error_1);
                            break;
                        case HAEAiDubbingError.ERR_NET_CONNECT_FAILED:
                            msg = AiDubbingAudioActivity.this.getResources()
                                    .getString(R.string.text_to_audio_error_2);
                            break;
                        case HAEAiDubbingError.ERR_INSUFFICIENT_BALANCE:
                            msg = AiDubbingAudioActivity.this.getResources()
                                    .getString(R.string.text_to_audio_error_3);
                            break;
                        case HAEAiDubbingError.ERR_SPEECH_SYNTHESIS_FAILED:
                            msg = AiDubbingAudioActivity.this.getResources()
                                    .getString(R.string.text_to_audio_error_4);
                            break;
                        case HAEAiDubbingError.ERR_AUDIO_PLAYER_FAILED:
                            msg = AiDubbingAudioActivity.this.getResources()
                                    .getString(R.string.text_to_audio_error_5);
                            break;
                        case HAEAiDubbingError.ERR_AUTHORIZE_FAILED:
                            msg = AiDubbingAudioActivity.this.getResources()
                                    .getString(R.string.text_to_audio_error_6);
                            break;
                        case HAEAiDubbingError.ERR_AUTHORIZE_TOKEN_INVALID:
                            msg = AiDubbingAudioActivity.this.getResources()
                                    .getString(R.string.text_to_audio_error_7);
                            break;
                        case HAEAiDubbingError.ERR_INTERNAL:
                            msg = AiDubbingAudioActivity.this.getResources()
                                    .getString(R.string.text_to_audio_error_8);
                            break;
                        case HAEAiDubbingError.ERR_UNKNOWN:
                            msg = AiDubbingAudioActivity.this.getResources()
                                    .getString(R.string.text_to_audio_error_9);
                            break;
                        case HAEAiDubbingError.ERR_TEXT:
                            msg = AiDubbingAudioActivity.this
                                            .getResources().getString(R.string.error_2002);
                            break;
                        case HAEAiDubbingError.ERR_SPEAKER_LAN:
                            msg = AiDubbingAudioActivity.this
                                            .getResources().getString(R.string.error_80005);
                            break;
                        default:
                            msg = AiDubbingAudioActivity.this.getResources()
                                            .getString(R.string.text_to_audio_error);
                            break;
                    }
                    addBtn.setText(R.string.replay);
                    Toast.makeText(AiDubbingAudioActivity.this, msg,Toast.LENGTH_SHORT).show();
                });
    }

    private AiDubbingLanguageAdapter aiDubbingLanguageAdapter;
    private AiDubbingStyleAdapter aiDubbingStyleAdapter;
    private List<String> languageCodeList = new ArrayList<>();
    private List<String> languageDescList = new ArrayList<>();

    private List<String> speakerCodeList = new ArrayList<>();
    private List<String> speakerTypeList = new ArrayList<>();
    private String defaultLanguageCode = "";
    private String defaultLanguageDesc = "";
    private String defaultSpeakerCode = "";
    private int defaultSpeakerType = -1;

    private void stopAiDubbing() {
        if (mEngine != null) {
            mEngine.stop();
            mEngine = null;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (isShouldHideInput(v, ev)) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null && v != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
            return super.dispatchTouchEvent(ev);
        }
        // Necessary, otherwise all components will not have touch event
        if (getWindow().superDispatchTouchEvent(ev)) {
            return true;
        }
        return onTouchEvent(ev);
    }

    /**
     * Hide keyboard or not
     *
     * @param v     view
     * @param event event
     * @return true of false
     */
    public boolean isShouldHideInput(View v, MotionEvent event) {
        if ((v instanceof EditText)) {
            int[] leftTop = {0, 0};
            // Get the current location of the input view
            v.getLocationInWindow(leftTop);
            int left = leftTop[0];
            int top = leftTop[1];
            int bottom = top + v.getHeight();
            int right = left + v.getWidth();
            // Click the input box area to keep the event of clicking EditText
            return !(event.getX() > left)
                    || !(event.getX() < right)
                    || !(event.getY() > top)
                    || !(event.getY() < bottom);
        }
        return false;
    }

    private Dialog languageDialog;

    private void createLanguageDialog() {
        this.languageDialog = new Dialog(this, R.style.MyDialogStyle);
        View view = View.inflate(this, R.layout.dialog_dubbing_language, null);
        // Set up a custom layout
        this.languageDialog.setContentView(view);

        RecyclerView languageRv = view.findViewById(R.id.language_rv);
        languageCodeList = mEngine.getLanguages();
        languageDescList = mEngine.getLanguagesDesc();
        if (languageCodeList == null ||languageCodeList.size() == 0){
            initSpeakerList();
            return;
        }
        List<String> languageList = new ArrayList<>();
        if (languageCodeList != null) {
            if (defaultLanguageCode.equals("")) {
                defaultLanguageCode = languageCodeList.get(0);
            }
            if (defaultLanguageDesc.equals("")) {
                defaultLanguageDesc = languageDescList.get(0);
            }
            languageList.addAll(languageDescList);
        }
        languageText.setText(defaultLanguageDesc);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        languageRv.setLayoutManager(linearLayoutManager);
        languageRv.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        // Initialize the adapter.
        aiDubbingLanguageAdapter = new AiDubbingLanguageAdapter(this, languageList,defaultLanguageDesc);

        // Set adapter.
        languageRv.setAdapter(aiDubbingLanguageAdapter);
        // Set Entry Click Event
        aiDubbingLanguageAdapter.setOnItemClickListener(this);

        this.languageDialog.setCanceledOnTouchOutside(true);
        // Set the size of the dialog
        Window dialogWindow = this.languageDialog.getWindow();
        if (dialogWindow != null) {
            WindowManager.LayoutParams layoutParams = dialogWindow.getAttributes();
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            layoutParams.gravity = Gravity.BOTTOM;
            dialogWindow.setAttributes(layoutParams);
        }
    }

    private Dialog speakerDialog;

    private void createStyleDialog(List<TextToSpeechStyleBean> divideBeanList) {
        speakerCodeList.clear();
        speakerTypeList.clear();
        this.speakerDialog = new Dialog(this, R.style.MyDialogStyle);
        View view = View.inflate(this, R.layout.dialog_style, null);
        this.speakerDialog.setContentView(view);

        RecyclerView styleRv = view.findViewById(R.id.dubbing_style_rv);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        styleRv.setLayoutManager(linearLayoutManager);
        styleRv.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        for (TextToSpeechStyleBean divideBean : divideBeanList) {
            speakerCodeList.add(divideBean.getSpeaker().getSpeakerDesc());
            speakerTypeList.add(divideBean.getSpeaker().getName());
        }
        if(defaultSpeakerType == -1){
            defaultSpeakerCode = divideBeanList.get(0).getSpeaker().getSpeakerDesc();
            defaultSpeakerType = Integer.parseInt(divideBeanList.get(0).getSpeaker().getName());
            speakerText.setText(defaultSpeakerCode);
        }
        aiDubbingStyleAdapter = new AiDubbingStyleAdapter(this, speakerCodeList , defaultSpeakerCode);

        // set adapter
        styleRv.setAdapter(aiDubbingStyleAdapter);
        // Set Entry Click Event
        aiDubbingStyleAdapter.setOnItemClickListener(this);

        this.speakerDialog.setCanceledOnTouchOutside(true);
        // Set the size of the dialog
        Window dialogWindow = this.speakerDialog.getWindow();
        if (dialogWindow != null) {
            WindowManager.LayoutParams layoutParams = dialogWindow.getAttributes();
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            layoutParams.gravity = Gravity.BOTTOM;
            dialogWindow.setAttributes(layoutParams);
        }
        showStyleDialog();
    }

    private Dialog playModeDialog;
    private TextView textQueue;
    private TextView textClear;

    private void createModeDialog() {
        this.playModeDialog = new Dialog(this, R.style.MyDialogStyle);
        View view = View.inflate(this, R.layout.dialog_mode, null);
        this.playModeDialog.setContentView(view);
        this.textQueue = view.findViewById(R.id.queueing_mode);
        this.textQueue.setOnClickListener(this);
        this.textClear = view.findViewById(R.id.clear_mode);
        this.textClear.setOnClickListener(this);
        this.playModeDialog.setCanceledOnTouchOutside(true);
        Window dialogWindow = this.playModeDialog.getWindow();
        if (dialogWindow != null) {
            WindowManager.LayoutParams layoutParams = dialogWindow.getAttributes();
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            layoutParams.gravity = Gravity.BOTTOM;
            dialogWindow.setAttributes(layoutParams);
        }
    }

    private void initModeDialogViews() {
        this.textQueue.setSelected(false);
        this.textClear.setSelected(false);
        if (isFlush) {
            this.textClear.setSelected(true);
        } else {
            this.textQueue.setSelected(true);
        }
    }

    private void initAction() {
        addBtn.setOnClickListener(this);
        pauseBtn.setOnClickListener(this);
        stopBtn.setOnClickListener(this);
        playBtn.setOnClickListener(this);

        speedSeek.setOnSeekBarChangeListener(this);
        volumeSeek.setOnSeekBarChangeListener(this);
        clear.setOnClickListener(this);

        rl_language.setOnClickListener(this);
        rl_speaker.setOnClickListener(this);
        rl_mode.setOnClickListener(this);
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "onDestroy");
        super.onDestroy();
        if (mEngine != null) {
            mEngine.stop();
            mEngine = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.close:
                editText.setText("");
                break;
            case R.id.btn_play:
                if (defaultLanguageCode.equals("")){
                    Toast.makeText(AiDubbingAudioActivity.this, this.getResources().getString(R.string.select_lan),Toast.LENGTH_SHORT).show();
                    return;
                }
                if (defaultSpeakerCode.equals("")){
                    Toast.makeText(AiDubbingAudioActivity.this, this.getResources().getString(R.string.select_style),Toast.LENGTH_SHORT).show();
                    return;
                }
                // Use the system player to play the cached audio.
                isSpeechNoPreview = false;
                isSaveSpeechToFile = false;
                HAEAiDubbingConfig mConfig = generateConfig();
                initAiDubbing(mConfig);
                // Invoke Ai to perform voice conversion.
                String text = editText.getText().toString();
                if (TextUtils.isEmpty(text)) {
                    Toast.makeText(
                                    AiDubbingAudioActivity.this,
                                    AiDubbingAudioActivity.this.getResources().getString(R.string.text_to_speech_toast_1),
                                    Toast.LENGTH_SHORT)
                            .show();
                } else {
                    String taskId = mEngine.speak(text, aiMode);
                    temp.put(taskId, text);
                }
                break;
            case R.id.btn_add:
                if (defaultLanguageCode.equals("") || defaultLanguageDesc.equals("")){
                    Toast.makeText(AiDubbingAudioActivity.this, this.getResources().getString(R.string.select_lan),Toast.LENGTH_SHORT).show();
                    return;
                }
                if (defaultSpeakerCode.equals("")){
                    Toast.makeText(AiDubbingAudioActivity.this, this.getResources().getString(R.string.select_style),Toast.LENGTH_SHORT).show();
                    return;
                }
                if (addBtn.getText().toString().equals(getResources().getString(R.string.queue_add_center))) {
                    Toast.makeText(
                                    AiDubbingAudioActivity.this,
                                    AiDubbingAudioActivity.this.getResources().getString(R.string.queue_add_center),
                                    Toast.LENGTH_SHORT)
                            .show();
                    return;
                }
                String s = editText.getText().toString();
                // Invoke Ai to perform voice conversion.
                if (TextUtils.isEmpty(s)) {
                    Toast.makeText(
                                    AiDubbingAudioActivity.this,
                                    AiDubbingAudioActivity.this.getResources().getString(R.string.text_to_speech_toast_1),
                                    Toast.LENGTH_SHORT)
                            .show();
                } else {
                    addBtn.setText(R.string.queue_add_center);
                    isSaveSpeechToFile = true;
                    isSpeechNoPreview = true;
                    HAEAiDubbingConfig config = generateConfig();
                    initAiDubbing(config);
                    String taskId = mEngine.speak(s, aiMode);
                    temp.put(taskId, s);
                }
                break;
            case R.id.btn_pause:
                if (mEngine != null) {
                    isPause = !isPause;
                    pauseBtn.setText(isPause ? R.string.resume : R.string.pause);
                    if (isPause) {
                        mEngine.pause();
                    } else {
                        mEngine.resume();
                    }
                }
                break;
            case R.id.btn_stop:
                isPause = false;
                pauseBtn.setText(R.string.pause);
                mEngine.stop();
                break;
            case R.id.rl_language:
                if (!NetworkUtil.isNetworkConnected()) {
                    Toast.makeText(AiDubbingAudioActivity.this, this.getResources().getString(R.string.text_to_audio_error_2),Toast.LENGTH_SHORT).show();
                    return;
                }
                if(mEngine == null || mEngine.getLanguages() == null){
                    HAEAiDubbingConfig mlConfigs = new HAEAiDubbingConfig();
                    mEngine = new HAEAiDubbingEngine(mlConfigs);
                    mEngine.setAiDubbingCallback(callback);
                }
                createLanguageDialog();
                showLanguageDialog();
                break;
            case R.id.rl_style:
                if (defaultLanguageCode.equals("") || defaultLanguageDesc.equals("")){
                    Toast.makeText(AiDubbingAudioActivity.this, this.getResources().getString(R.string.select_lan),Toast.LENGTH_SHORT).show();
                    return;
                }
                speakerList = mEngine.getSpeakerNoRequest(defaultLanguageCode);
                if (speakerList != null && speakerList.size() > 0) {
                    createStyleDialog(generateList(speakerList));
                }
                break;
            case R.id.rl_mode:
                showModeDialog();
                break;
            case R.id.queueing_mode:
                isFlush = false;
                modeText.setText(playModeResources[0]);
                this.playModeDialog.dismiss();
                break;
            case R.id.clear_mode:
                isFlush = true;
                modeText.setText(playModeResources[1]);
                this.playModeDialog.dismiss();
                break;
            default:
                break;
        }
    }

    private void showStyleDialog() {
        speakerDialog.show();
    }

    private void showLanguageDialog() {
        languageDialog.show();
    }

    private void showModeDialog() {
        initModeDialogViews();
        playModeDialog.show();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        switch (seekBar.getId()) {
            case R.id.volumeSeek:
                int p = progress + 90;
                if (progress == 90) {
                    String text1 = 90 + "";
                    seekBar.setProgress(0);
                    textView_volume.setText(text1);
                } else {
                    textView_volume.setText(p + "");
                }
                break;
            case R.id.speedSeek:
                int s = progress + 50;
                String text = progress + "";
                if (progress == 50) {
                    String text1 = 50 + "";
                    seekBar.setProgress(50);
                    textView_speed.setText(text1);
                } else {
                    textView_speed.setText(s + "");
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        switch (seekBar.getId()) {
            case R.id.volumeSeek: // volumeSeek
                volumeVal = seekBar.getProgress() + 90;
                break;
            case R.id.speedSeek: // speedSeek
                speedVal = seekBar.getProgress() + 50;
                break;
            default:
                break;
        }
    }

    @Override
    public void setOnLanguageItemClick(View view, int position) {
        defaultLanguageCode = languageCodeList.get(position);
        defaultLanguageDesc = languageDescList.get(position);
        languageText.setText(defaultLanguageDesc);
        // updateConfig();
        aiDubbingLanguageAdapter.notifyDataSetChanged();
        languageDialog.dismiss();
        List<HAEAiDubbingSpeaker> speakerList = mEngine.getSpeakerNoRequest(defaultLanguageCode);
        defaultSpeakerCode = speakerList.get(0).getSpeakerDesc();
        defaultSpeakerType = Integer.parseInt(speakerList.get(0).getName());
        speakerText.setText(defaultSpeakerCode);
    }

    @Override
    public void setOnStyleItemClick(View view, int position) {
        defaultSpeakerCode = speakerCodeList.get(position);
        defaultSpeakerType = Integer.parseInt(speakerTypeList.get(position));
        speakerText.setText(defaultSpeakerCode);
        // updateConfig();
        aiDubbingStyleAdapter.notifyDataSetChanged();
        speakerDialog.dismiss();
    }

    private final List<TextToSpeechStyleBean> listData = new ArrayList<>();
    private List<HAEAiDubbingSpeaker> speakerList;
    private void initSpeakerList() {
        listData.clear();
        initAiDubbing(null);
        speakerList = mEngine.getSpeaker(defaultLanguageCode);
    }

    private String getAudioFileNameByTask(String taskId, String fileType) {
        String filePath = "";
        if (TextUtils.isEmpty(taskId) || TextUtils.isEmpty(fileType)) {
            return filePath;
        }
        String cachePath = Environment.getExternalStorageDirectory().getAbsolutePath();
        String mFolder = cachePath + File.separator + AI_DUBBING_PATH;
        File file = new File(mFolder);
        if (!file.exists()) {
            file.mkdirs();
        }

        filePath = mFolder + File.separator + taskId + fileType;
        return filePath;
    }

    private boolean isSpeechNoPreview = false; // Whether to preview and play the returned voice streams.
    private boolean isSaveSpeechToFile = true; // Indicates whether to save the file.
    private int aiMode;

    private void initAiDubbing(HAEAiDubbingConfig mConfig) {
        if (mConfig == null) {
            mConfig = generateConfig();
        }

        if (mEngine == null) {
            mEngine = new HAEAiDubbingEngine(mConfig);
        } else {
            mEngine.updateConfig(mConfig);
        }
        // Set playback callback
        mEngine.setAiDubbingCallback(callback);
    }

    private HAEAiDubbingConfig generateConfig() {
        if (isFlush) {
            aiMode = HAEAiDubbingEngine.QUEUE_FLUSH;
        } else {
            aiMode = HAEAiDubbingEngine.QUEUE_APPEND;
        }
        if (isSpeechNoPreview) {
            aiMode |= HAEAiDubbingEngine.EXTERNAL_PLAYBACK;
        }
        if (isSaveSpeechToFile) {
            aiMode |= HAEAiDubbingEngine.OPEN_STREAM;
        }
        HAEAiDubbingConfig mlConfigs =
                new HAEAiDubbingConfig().setVolume(volumeVal).setSpeed(speedVal).setType(defaultSpeakerType).setLanguage(defaultLanguageCode);
        return mlConfigs;
    }

    private List<TextToSpeechStyleBean> generateList(List<HAEAiDubbingSpeaker> speakerList) {
        List<TextToSpeechStyleBean> beanList = new ArrayList<>();
        for (int i = 0; i < speakerList.size(); i++) {
            TextToSpeechStyleBean bean = new TextToSpeechStyleBean(speakerList.get(i), false);
            beanList.add(bean);
        }
        return beanList;
    }
}
