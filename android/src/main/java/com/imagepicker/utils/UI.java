package com.imagepicker.utils;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.content.Context;
import android.content.DialogInterface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableNativeMap;
import com.imagepicker.ImagePickerModule;
import com.imagepicker.R;

import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

import java.lang.ref.WeakReference;

/**
 * @author Alexander Ustinov
 */
public class UI {

    public static @NonNull
    AlertDialog chooseDialog(@Nullable final ImagePickerModule module,
            @NonNull final ReadableMap options,
            @Nullable final OnAction callback) {
        final Context context = module.getActivity();
        if (context == null) {
            return null;
        }
        final WeakReference<ImagePickerModule> reference = new WeakReference<>(module);

        AlertDialog.Builder builder = new AlertDialog.Builder(context, module.getDialogThemeId());
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View view = layoutInflater.inflate(R.layout.view_take_image_picker, null);
        builder.setView(view);
        final AlertDialog dialog = builder.create();
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(@NonNull final DialogInterface dialog) {
                callback.onCancel(reference.get());
                dialog.dismiss();
            }
        });

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(@NonNull final DialogInterface dialog) {
                callback.onCancel(reference.get());
                dialog.dismiss();
            }
        });
        //自定义布局 begin
        view.findViewById(R.id.take_image_open_album)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        callback.onUseLibrary(reference.get());
                        dialog.dismiss();
                    }
                });
        view.findViewById(R.id.take_image_open_camera)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        callback.onTakePhoto(reference.get());
                        dialog.dismiss();
                    }
                });
        view.findViewById(R.id.take_image_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.onCancel(reference.get());
                dialog.dismiss();
            }
        });
        view.findViewById(R.id.take_image_default_photo)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        callback.onCustomButton(reference.get());
                        dialog.dismiss();
                    }
                });

        ReadableNativeMap map = (ReadableNativeMap) options;
        if (map.hasKey("customButtons")) {
            ReadableArray array = map.getArray("customButtons");
            if (array != null && array.size() > 0) {
                view.findViewById(R.id.take_image_default_photo).setVisibility(View.VISIBLE);
            } else {
                view.findViewById(R.id.take_image_default_photo).setVisibility(View.GONE);
            }
        }
        Window window = dialog.getWindow();
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setWindowAnimations(R.style.dialog_animation);
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.gravity = Gravity.BOTTOM;
        window.setAttributes(lp);
        return dialog;
    }

    public interface OnAction {

        void onTakePhoto(@Nullable ImagePickerModule module);

        void onUseLibrary(@Nullable ImagePickerModule module);

        void onCancel(@Nullable ImagePickerModule module);

        void onCustomButton(@Nullable ImagePickerModule module);
    }
}
