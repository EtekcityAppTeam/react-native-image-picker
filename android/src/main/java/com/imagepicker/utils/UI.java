package com.imagepicker.utils;

import android.view.View;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.ColorDrawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.widget.ArrayAdapter;

import com.facebook.react.bridge.ReadableMap;
import com.imagepicker.ImagePickerModule;
import com.imagepicker.R;

import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * @author Alexander Ustinov
 */
public class UI
{
    public static @NonNull AlertDialog chooseDialog(@Nullable final ImagePickerModule module,
                                                    @NonNull final ReadableMap options,
                                                    @Nullable final OnAction callback)
    {
        final Context context = module.getActivity();
        if (context == null)
        {
            return null;
        }
        final WeakReference<ImagePickerModule> reference = new WeakReference<>(module);

//        final ButtonsHelper buttons = ButtonsHelper.newInstance(options);
//        final List<String> titles = buttons.getTitles();
//        final List<String> actions = buttons.getActions();
//        ArrayAdapter<String> adapter = new ArrayAdapter<>(
//                context,
//                R.layout.list_item,
//                titles
//        );
//        AlertDialog.Builder builder = new AlertDialog.Builder(context, module.getDialogThemeId() /*android.R.style.Theme_Holo_Light_Dialog*/);
//        if (ReadableMapUtils.hasAndNotEmptyString(options, "title"))
//        {
//            builder.setTitle(options.getString("title"));
//        }
//
//        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
//            public void onClick(DialogInterface dialog, int index) {
//                final String action = actions.get(index);
//
//                switch (action) {
//                    case "photo":
//                        callback.onTakePhoto(reference.get());
//                        break;
//
//                    case "library":
//                        callback.onUseLibrary(reference.get());
//                        break;
//
//                    case "cancel":
//                        callback.onCancel(reference.get());
//                        break;
//
//                    default:
//                        callback.onCustomButton(reference.get(), action);
//                }
//            }
//        });
//
//        builder.setNegativeButton(buttons.btnCancel.title, new DialogInterface.OnClickListener()
//        {
//            @Override
//            public void onClick(DialogInterface dialogInterface,
//                                int i)
//            {
//                callback.onCancel(reference.get());
//                dialogInterface.dismiss();
//            }
//        });
//
//        final AlertDialog dialog = builder.create();
//
//        dialog.setOnCancelListener(new DialogInterface.OnCancelListener()
//        {
//            @Override
//            public void onCancel(@NonNull final DialogInterface dialog)
//            {
//                callback.onCancel(reference.get());
//                dialog.dismiss();
//            }
//        });

        AlertDialog.Builder builder = new AlertDialog.Builder(context,module.getDialogThemeId());
        View view = View.inflate(context,R.layout.view_take_image,null);
        builder.setView(view);


        final AlertDialog dialog = builder.create();
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener()
        {
            @Override
            public void onCancel(@NonNull final DialogInterface dialog)
            {
                callback.onCancel(reference.get());
                dialog.dismiss();
            }
        });

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener()
        {
            @Override
            public void onCancel(@NonNull final DialogInterface dialog)
            {
                callback.onCancel(reference.get());
                dialog.dismiss();
            }
        });
        //自定义布局 begin
        view.findViewById(R.id.take_image_open_album).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.onUseLibrary(reference.get());
                dialog.dismiss();
            }
        });
        view.findViewById(R.id.take_image_open_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.onTakePhoto(reference.get());
                dialog.dismiss();
            }
        });

//        view.findViewById(R.id.take_image_default_photo).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                //TODO
//            }
//        });
        Window window = dialog.getWindow();
        window.setGravity(Gravity.BOTTOM);
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setWindowAnimations(R.style.dialog_animation);
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(lp);



        return dialog;
    }

    public interface OnAction
    {
        void onTakePhoto(@Nullable ImagePickerModule module);
        void onUseLibrary(@Nullable ImagePickerModule module);
        void onCancel(@Nullable ImagePickerModule module);
        void onCustomButton(@Nullable ImagePickerModule module, String action);
    }
}
