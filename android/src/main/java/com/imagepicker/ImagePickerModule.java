package com.imagepicker;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Patterns;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.facebook.react.ReactActivity;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.modules.core.PermissionListener;
import com.imagepicker.media.ImageConfig;
import com.imagepicker.permissions.OnImagePickerPermissionsCallback;
import com.imagepicker.utils.MediaUtils.ReadExifResult;
import com.imagepicker.utils.RealPathUtil;
import com.imagepicker.utils.UI;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static com.imagepicker.utils.MediaUtils.RolloutPhotoResult;
import static com.imagepicker.utils.MediaUtils.createNewFile;
import static com.imagepicker.utils.MediaUtils.fileScan;
import static com.imagepicker.utils.MediaUtils.getResizedImage;
import static com.imagepicker.utils.MediaUtils.readExifInterface;
import static com.imagepicker.utils.MediaUtils.removeUselessFiles;
import static com.imagepicker.utils.MediaUtils.rolloutPhotoFromCamera;

public class ImagePickerModule extends ReactContextBaseJavaModule
        implements ActivityEventListener {
    public static final int REQUEST_LAUNCH_IMAGE_CAPTURE = 13001;
    public static final int REQUEST_LAUNCH_IMAGE_LIBRARY = 13002;
    public static final int REQUEST_LAUNCH_VIDEO_LIBRARY = 13003;
    public static final int REQUEST_LAUNCH_VIDEO_CAPTURE = 13004;
    public static final int REQUEST_PERMISSIONS_FOR_CAMERA = 14001;
    public static final int REQUEST_PERMISSIONS_FOR_LIBRARY = 14002;
    public static final int PHOTO_REQUEST_CUT = 13005;

    private final ReactApplicationContext reactContext;
    private final int dialogThemeId;
    protected Callback callback;
    private ReadableMap options;
    protected Uri cameraCaptureURI;
    private Boolean noData = false;
    private Boolean pickVideo = false;
    private ImageConfig imageConfig = new ImageConfig(null, null, 0, 0, 100, 0, false);

    @Deprecated
    private int videoQuality = 1;
    @Deprecated
    private int videoDurationLimit = 0;
    private ResponseHelper responseHelper = new ResponseHelper();
    private PermissionListener listener = new PermissionListener() {
        public boolean onRequestPermissionsResult(final int requestCode,
                                                  @NonNull final String[] permissions,
                                                  @NonNull final int[] grantResults) {
            boolean permissionsGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                final boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                permissionsGranted = permissionsGranted && granted;
            }
            if (callback == null || options == null) {
                return false;
            }
            if (!permissionsGranted) {
                if (requestCode == REQUEST_PERMISSIONS_FOR_LIBRARY){
                    boolean isTip = ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    if (!isTip){
                        showDialogIos(getActivity().getString(R.string.permission_tip_photo));
                    }
                }else if (requestCode == REQUEST_PERMISSIONS_FOR_CAMERA){
                    boolean isTipCamera = ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.CAMERA);
                    boolean isTipSD = ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    if ((!isTipCamera) && (!isTipSD)){
                        showDialogIos(getActivity().getString(R.string.permission_tip_camera));
                    }
                }
                responseHelper.invokeError(callback, "Permissions weren't granted");
                return false;
            }
            switch (requestCode) {
                case REQUEST_PERMISSIONS_FOR_CAMERA:
                    launchCamera(options, callback);
                    break;
                case REQUEST_PERMISSIONS_FOR_LIBRARY:
                    launchImageLibrary(options, callback);
                    break;
            }
            return true;
        }
    };

    public ImagePickerModule(ReactApplicationContext reactContext,
                             @StyleRes final int dialogThemeId) {
        super(reactContext);
        this.dialogThemeId = dialogThemeId;
        this.reactContext = reactContext;
        this.reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "ImagePickerManager";
    }

    @ReactMethod
    public void showImagePicker(final ReadableMap options, final Callback callback) {
        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            responseHelper.invokeError(callback, "can't find current Activity");
            return;
        }
        this.callback = callback;
        this.options = options;
        imageConfig = new ImageConfig(null, null, 0, 0, 100, 0, false);
        final AlertDialog dialog = UI.chooseDialog(this, options, new UI.OnAction() {
            @Override
            public void onTakePhoto(@NonNull final ImagePickerModule module) {
                module.launchCamera();
            }

            @Override
            public void onUseLibrary(@NonNull final ImagePickerModule module) {
                module.launchImageLibrary();
            }

            @Override
            public void onCancel(@NonNull final ImagePickerModule module) {
                module.doOnCancel();
            }

            @Override
            public void onCustomButton(@NonNull final ImagePickerModule module) {
                module.invokeCustomButton();
            }
        });
        dialog.show();
    }

    public void doOnCancel() {
        if (callback != null){
            responseHelper.invokeCancel(callback);
            callback = null;
        }

    }

    public void launchCamera() {
        this.launchCamera(this.options, this.callback);
    }

    // NOTE: Currently not reentrant / doesn't support concurrent requests
    @ReactMethod
    public void launchCamera(final ReadableMap options, final Callback callback) {
        if (!isCameraAvailable()) {
            responseHelper.invokeError(callback, "Camera not available");
            return;
        }
        final Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            responseHelper.invokeError(callback, "can't find current Activity");
            return;
        }
        this.callback = callback;
        this.options = options;
        if (!permissionsCheck(currentActivity, callback, REQUEST_PERMISSIONS_FOR_CAMERA)) {
            return;
        }
        parseOptions(this.options);
        int requestCode;
        Intent cameraIntent;
        if (pickVideo) {
            requestCode = REQUEST_LAUNCH_VIDEO_CAPTURE;
            cameraIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, videoQuality);
            if (videoDurationLimit > 0) {
                cameraIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, videoDurationLimit);
            }
        } else {
            requestCode = REQUEST_LAUNCH_IMAGE_CAPTURE;
            cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            final File original = createNewFile(reactContext, this.options, false);
            imageConfig = imageConfig.withOriginalFile(original);
            if (imageConfig.original != null){
                cameraCaptureURI = RealPathUtil.compatUriFromFile(reactContext, imageConfig.original);
            }else {
                responseHelper.invokeError(callback, "Couldn't get file path for photo");
                return;
            }
            if (cameraCaptureURI == null) {
                responseHelper.invokeError(callback, "Couldn't get file path for photo");
                return;
            }
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraCaptureURI);
        }
        if (cameraIntent.resolveActivity(reactContext.getPackageManager()) == null) {
            responseHelper.invokeError(callback, "Cannot launch camera");
            return;
        }
        // Workaround for Android bug.
        // grantUriPermission also needed for KITKAT,
        // see https://code.google.com/p/android/issues/detail?id=76683
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            List<ResolveInfo> resInfoList = reactContext.getPackageManager().queryIntentActivities(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                reactContext.grantUriPermission(packageName, cameraCaptureURI, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }

        try {
            currentActivity.startActivityForResult(cameraIntent, requestCode);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
            responseHelper.invokeError(callback, "Cannot launch camera");
        }
    }

    public void launchImageLibrary() {
        this.launchImageLibrary(this.options, this.callback);
    }

    // NOTE: Currently not reentrant / doesn't support concurrent requests
    @ReactMethod
    public void launchImageLibrary(final ReadableMap options, final Callback callback) {
        final Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            responseHelper.invokeError(callback, "can't find current Activity");
            return;
        }
        this.callback = callback;
        this.options = options;
        if (!permissionsCheck(currentActivity, callback, REQUEST_PERMISSIONS_FOR_LIBRARY)) {
            return;
        }
        parseOptions(this.options);
        int requestCode;
        Intent libraryIntent;
        if (pickVideo) {
            requestCode = REQUEST_LAUNCH_VIDEO_LIBRARY;
            libraryIntent = new Intent(Intent.ACTION_PICK);
            libraryIntent.setType("video/*");
        } else {
            requestCode = REQUEST_LAUNCH_IMAGE_LIBRARY;
            libraryIntent = new Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        }
        if (libraryIntent.resolveActivity(reactContext.getPackageManager()) == null) {
            responseHelper.invokeError(callback, "Cannot launch photo library");
            return;
        }
        try {
            currentActivity.startActivityForResult(libraryIntent, requestCode);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
            responseHelper.invokeError(callback, "Cannot launch photo library");
        }
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        //robustness code
        if (passResult(requestCode)) {
            return;
        }
        responseHelper.cleanResponse();
        // user cancel
        if (resultCode != Activity.RESULT_OK) {
            removeUselessFiles(requestCode, imageConfig);
            responseHelper.invokeCancel(callback);
            callback = null;
            return;
        }
        Uri uri = null;
        switch (requestCode) {
            case REQUEST_LAUNCH_IMAGE_CAPTURE:
                uri = cameraCaptureURI;
                break;
            //add 20170831
            case PHOTO_REQUEST_CUT:
                uri = cropPictureURI;
                imageConfig = imageConfig.withOriginalFile(new File(cropPicturePath));
                break;
            //end 20170831
            case REQUEST_LAUNCH_IMAGE_LIBRARY:
                uri = data.getData();
                String realPath = getRealPathFromURI(uri);
                final boolean isUrl = !TextUtils.isEmpty(realPath) &&
                        Patterns.WEB_URL.matcher(realPath).matches();
                if (realPath == null || isUrl) {
                    try {
                        File file = createFileFromURI(uri);
                        realPath = file.getAbsolutePath();
                        uri = Uri.fromFile(file);

                    } catch (Exception e) {
                        // image not in cache
                        responseHelper.putString("error", "Could not read photo");
                        responseHelper.putString("uri", uri.toString());
                        responseHelper.invokeResponse(callback);
                        callback = null;
                        return;
                    }
                }

                imageConfig = imageConfig.withOriginalFile(new File(realPath));
                break;
            case REQUEST_LAUNCH_VIDEO_LIBRARY:
                responseHelper.putString("uri", data.getData().toString());
                responseHelper.putString("path", getRealPathFromURI(data.getData()));
                responseHelper.invokeResponse(callback);
                callback = null;
                return;
            case REQUEST_LAUNCH_VIDEO_CAPTURE:
                final String path = getRealPathFromURI(data.getData());
                responseHelper.putString("uri", data.getData().toString());
                responseHelper.putString("path", path);
                fileScan(reactContext, path);
                responseHelper.invokeResponse(callback);
                callback = null;
                return;
        }
        final ReadExifResult result = readExifInterface(responseHelper, imageConfig);
        if (result.error != null) {
            removeUselessFiles(requestCode, imageConfig);
            responseHelper.invokeError(callback, result.error.getMessage());
            callback = null;
            return;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageConfig.original.getAbsolutePath(), options);
        int initialWidth = options.outWidth;
        int initialHeight = options.outHeight;
        updatedResultResponse(uri, imageConfig.original.getAbsolutePath());
        // don't create a new file if contraint are respected
        if (imageConfig.useOriginal(initialWidth, initialHeight, result.currentRotation)) {
            responseHelper.putInt("width", initialWidth);
            responseHelper.putInt("height", initialHeight);
            fileScan(reactContext, imageConfig.original.getAbsolutePath());
        } else {
            imageConfig = getResizedImage(reactContext, this.options, imageConfig, initialWidth, initialHeight, requestCode);
            if (imageConfig.resized == null) {
                removeUselessFiles(requestCode, imageConfig);
                responseHelper.putString("error", "Can't resize the image");
            } else {
                uri = Uri.fromFile(imageConfig.resized);
                BitmapFactory.decodeFile(imageConfig.resized.getAbsolutePath(), options);
                responseHelper.putInt("width", options.outWidth);
                responseHelper.putInt("height", options.outHeight);
                updatedResultResponse(uri, imageConfig.resized.getAbsolutePath());
                fileScan(reactContext, imageConfig.resized.getAbsolutePath());
            }
        }
        if (imageConfig.saveToCameraRoll && requestCode == REQUEST_LAUNCH_IMAGE_CAPTURE) {
            final RolloutPhotoResult rolloutResult = rolloutPhotoFromCamera(imageConfig);
            if (rolloutResult.error == null) {
                imageConfig = rolloutResult.imageConfig;
                uri = Uri.fromFile(imageConfig.getActualFile());
                updatedResultResponse(uri, imageConfig.getActualFile().getAbsolutePath());
            } else {
                removeUselessFiles(requestCode, imageConfig);
                final String errorMessage = new StringBuilder("Error moving image to camera roll: ")
                        .append(rolloutResult.error.getMessage()).toString();
                responseHelper.putString("error", errorMessage);
                return;
            }
        }
        //add 20170831
        if (REQUEST_LAUNCH_IMAGE_CAPTURE == requestCode || REQUEST_LAUNCH_IMAGE_LIBRARY == requestCode) {
          File file = new File(responseHelper.getResponse().getString("path"));
           cropPicture(activity, RealPathUtil.compatUriFromFile(reactContext, file));
            return;
        }
        //end 20170831
        responseHelper.invokeResponse(callback);
        callback = null;
        this.options = null;
    }

    public void invokeCustomButton() {
        responseHelper.invokeCustomButton(this.callback);
    }

    @Override
    public void onNewIntent(Intent intent) {
    }

    public Context getContext() {
        return getReactApplicationContext();
    }

    public
    @StyleRes
    int getDialogThemeId() {
        return this.dialogThemeId;
    }

    public
    @NonNull
    Activity getActivity() {
        return getCurrentActivity();
    }

    private boolean passResult(int requestCode) {
        return callback == null || (cameraCaptureURI == null && requestCode == REQUEST_LAUNCH_IMAGE_CAPTURE)
                || (cropPictureURI == null && requestCode == PHOTO_REQUEST_CUT)
                || (requestCode != REQUEST_LAUNCH_IMAGE_CAPTURE && requestCode != REQUEST_LAUNCH_IMAGE_LIBRARY
                && requestCode != REQUEST_LAUNCH_VIDEO_LIBRARY && requestCode != REQUEST_LAUNCH_VIDEO_CAPTURE
                && requestCode != PHOTO_REQUEST_CUT);
    }

    private void updatedResultResponse(@Nullable final Uri uri,
                                       @NonNull final String path) {
        responseHelper.putString("uri", uri.toString());
        responseHelper.putString("path", path);
        if (!noData) {
            responseHelper.putString("data", getBase64StringFromFile(path));
        }
        putExtraFileInfo(path, responseHelper);
    }

    private boolean permissionsCheck(@NonNull final Activity activity,
                                     @NonNull final Callback callback,
                                     @NonNull final int requestCode) {
        final int writePermission = ActivityCompat
                .checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        final int readPermission = ActivityCompat
                .checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE);
        final int cameraPermission = ActivityCompat
                .checkSelfPermission(activity, Manifest.permission.CAMERA);
        boolean permissionsGrated = false;
        if (requestCode == REQUEST_PERMISSIONS_FOR_LIBRARY) {
            permissionsGrated = writePermission == PackageManager.PERMISSION_GRANTED && readPermission == PackageManager.PERMISSION_GRANTED;
        } else if (requestCode == REQUEST_PERMISSIONS_FOR_CAMERA) {
            permissionsGrated = writePermission == PackageManager.PERMISSION_GRANTED && readPermission == PackageManager.PERMISSION_GRANTED && cameraPermission == PackageManager.PERMISSION_GRANTED;
        }
        if (!permissionsGrated) {
                String[] PERMISSIONS_CAMERA = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA};
                String[] PERMISSIONS_LIBRARY = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
                if (activity instanceof ReactActivity) {
                    if (requestCode == REQUEST_PERMISSIONS_FOR_LIBRARY) {
                        ((ReactActivity) activity).requestPermissions(PERMISSIONS_LIBRARY, requestCode, listener);
                    } else if (requestCode == REQUEST_PERMISSIONS_FOR_CAMERA) {
                        ((ReactActivity) activity).requestPermissions(PERMISSIONS_CAMERA, requestCode, listener);
                    }
                } else if (activity instanceof OnImagePickerPermissionsCallback) {
                    ((OnImagePickerPermissionsCallback) activity).setPermissionListener(listener);
                    if (requestCode == REQUEST_PERMISSIONS_FOR_LIBRARY) {
                        ActivityCompat.requestPermissions(activity, PERMISSIONS_LIBRARY, requestCode);
                    } else if (requestCode == REQUEST_PERMISSIONS_FOR_CAMERA) {
                        ActivityCompat.requestPermissions(activity, PERMISSIONS_CAMERA, requestCode);
                    }
                } else {
                    final String errorDescription = new StringBuilder(activity.getClass().getSimpleName())
                            .append(" must implement ")
                            .append(OnImagePickerPermissionsCallback.class.getSimpleName())
                            .toString();
                    throw new UnsupportedOperationException(errorDescription);
                }
                return false;
            }
        //}
        return true;
    }

    private boolean isCameraAvailable() {
        return reactContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)
                || reactContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    private
    @NonNull
    String getRealPathFromURI(@NonNull final Uri uri) {
        return RealPathUtil.getRealPathFromURI(reactContext, uri);
    }

    /**
     * Create a file from uri to allow image picking of image in disk cache
     * (Exemple: facebook image, google image etc..)
     *
     * @param uri
     * @return File
     * @throws Exception
     * @doc =>
     * https://github.com/nostra13/Android-Universal-Image-Loader#load--display-task-flow
     */
    private File createFileFromURI(Uri uri) throws Exception {
        File file = new File(reactContext.getExternalCacheDir(), "photo-" + uri.getLastPathSegment());
        InputStream input = reactContext.getContentResolver().openInputStream(uri);
        OutputStream output = new FileOutputStream(file);
        try {
            byte[] buffer = new byte[4 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        } finally {
            output.close();
            input.close();
        }
        return file;
    }

    private String getBase64StringFromFile(String absoluteFilePath) {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(new File(absoluteFilePath));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        byte[] bytes;
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        bytes = output.toByteArray();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private void putExtraFileInfo(@NonNull final String path,
                                  @NonNull final ResponseHelper responseHelper) {
        // size && filename
        try {
            File f = new File(path);
            responseHelper.putDouble("fileSize", f.length());
            responseHelper.putString("fileName", f.getName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        // type
        String extension = MimeTypeMap.getFileExtensionFromUrl(path);
        if (extension != null) {
            responseHelper.putString("type", MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension));
        }
    }


    private void parseOptions(final ReadableMap options) {
        noData = false;
        if (options.hasKey("noData")) {
            noData = options.getBoolean("noData");
        }
        imageConfig = imageConfig.updateFromOptions(options);
        pickVideo = false;
        if (options.hasKey("mediaType") && options.getString("mediaType").equals("video")) {
            pickVideo = true;
        }
        videoQuality = 1;
        if (options.hasKey("videoQuality") && options.getString("videoQuality").equals("low")) {
            videoQuality = 0;
        }
        videoDurationLimit = 0;
        if (options.hasKey("durationLimit")) {
            videoDurationLimit = options.getInt("durationLimit");
        }
    }

    Uri cropPictureURI = null;
    String cropPicturePath = null;

    private void cropPicture(Activity activity, Uri uri) {
        //解决当路径包含中文时，解析出错
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        cropPictureURI = Uri.fromFile(new File(path, "temp.jpg"));
        cropPicturePath = cropPictureURI.toString().replace("file://", "");

        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.setDataAndType(uri, "image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX", 800);
        intent.putExtra("outputY", 800);
        intent.putExtra("return-data", false);
        intent.putExtra("scale", true);
        intent.putExtra("scaleUpIfNeeded", true);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cropPictureURI);
        intent.putExtra("outputFormat", "JPEG");
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivityForResult(intent, PHOTO_REQUEST_CUT);
    }

    private void showDialogIos(String msg) {
        final Dialog dialog = new Dialog(getActivity(), R.style.customDialog);
        dialog.setContentView(R.layout.ios_dialog);
        dialog.show();
        dialog.setCanceledOnTouchOutside(true);
        TextView tvCancel = (TextView) dialog.findViewById(R.id.cancel);
        TextView tvOk = (TextView) dialog.findViewById(R.id.ok);
        TextView ios_dialog_title = (TextView) dialog.findViewById(R.id.ios_dialog_title);
        tvCancel.setText(getActivity().getString(R.string.pick_image_cancel));
        tvOk.setText(getActivity().getString(R.string.pick_image_ok));
        tvCancel.setTextColor(ContextCompat.getColor(getActivity(), R.color.colorAccent));
        tvOk.setTextColor(ContextCompat.getColor(getActivity(), R.color.colorAccent));
        ios_dialog_title.setText(msg);
        tvOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getActivity() == null){
                    dialog.dismiss();
                    return;
                }
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.fromParts("package", getActivity().getPackageName(), null));
                getActivity().startActivityForResult(intent, 1);
                dialog.dismiss();
            }
        });
        tvCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
    }
}
