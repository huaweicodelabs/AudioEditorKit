/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2020-2021. All rights reserved.
 */

package com.huawei.hms.audioeditor.demo.widget;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.huawei.hms.audioeditor.demo.R;
import com.huawei.hms.audioeditor.sdk.HAEAudioExpansion;

/**
 * @author wWX1043449
 * @date 2021/5/11
 * @since 2021/5/11
 */
public class ProgressDialog extends DialogFragment implements View.OnClickListener {
    public ProgressBar rd_progress;
    private TextView tv_progress;
    private TextView tv_message;
    private TextView tv_cancel;

    public static ProgressDialog newInstance(String message) {
        Bundle bundle = new Bundle();
        bundle.putString("message", message);
        ProgressDialog fragment = new ProgressDialog();
        fragment.setArguments(bundle);
        return fragment;
    }

    public void setProgress(int progress) {
        if (rd_progress != null) {
            rd_progress.setProgress(progress);
        }
        if (tv_progress != null) {
            tv_progress.setText(progress + "%");
        }
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Dialog dialog = getDialog();
        /* *  设置Dialog没有标题。需在setContentView之前设置，在之后设置会报错  * */
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        /* *  设置Dialog背景透明效果  * */
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        View view = requireActivity().getLayoutInflater().inflate(R.layout.progress_dialog, null);
        rd_progress = view.findViewById(R.id.pb_progress);
        tv_progress = view.findViewById(R.id.tv_progress);
        tv_message = view.findViewById(R.id.tv_message);
        tv_cancel = view.findViewById(R.id.tv_cancel);
        tv_cancel.setOnClickListener(this);
        if (getArguments() != null) {
            String message = getArguments().getString("message");
            if (!TextUtils.isEmpty(message)) {
                tv_message.setText(getArguments().getString("message"));
            }
        } else {
            tv_message.setVisibility(View.GONE);
        }

        return view;
    }

    @Override
    public void onStart() {
        /* *  设置宽度为屏宽、位置在屏幕中间  * */
        Window window = getDialog().getWindow();
        window.setBackgroundDrawableResource(R.color.transparent);
        WindowManager.LayoutParams wlp = window.getAttributes();
        wlp.gravity = Gravity.CENTER;
        wlp.width = WindowManager.LayoutParams.MATCH_PARENT;
        wlp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(wlp);
        setProgress(0);
        super.onStart();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.tv_cancel) {
            HAEAudioExpansion.getInstance().cancelExtractAudio();
        }
    }
}