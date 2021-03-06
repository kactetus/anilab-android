package com.xpyct.apps.anilab.managers.updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;

import com.afollestad.materialdialogs.MaterialDialog;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.xpyct.apps.anilab.BuildConfig;
import com.xpyct.apps.anilab.R;
import com.xpyct.apps.anilab.managers.Utils;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

public class WVersionManager implements IWVersionManager {
    private static final String TAG = "WVersionManager";

    private static final int MODE_CHECK_VERSION = 100;
    private static final int MODE_ASK_FOR_RATE = 200;

    private CustomTagHandler customTagHandler;

    private String PREF_IGNORE_VERSION_CODE = "w.ignore.version.code";
    private String PREF_REMINDER_TIME = "w.reminder.time";

    private Activity activity;
    private Drawable icon;
    private String title;
    private String message;
    private String updateNowLabel;
    private String remindMeLaterLabel;
    private String ignoreThisVersionLabel;
    private String updateUrl;
    private String versionContentUrl;
    private int reminderTimer;
    private int mVersionCode;
    private AlertDialogButtonListener listener;
    private boolean mDialogCancelable = true;
    private boolean mIsAskForRate = false;
    private String mAskForRatePositiveLabel;
    private String mAskForRateNegativeLabel;
    private int mMode = 100; // default mode
    private OnReceiveListener mOnReceiveListener;
    private String mResult;

    public WVersionManager(Activity act) {
        this.activity = act;
        this.listener = new AlertDialogButtonListener();
        this.customTagHandler = new CustomTagHandler();
    }

    private Drawable getDefaultAppIcon() {
        Drawable d = activity.getApplicationInfo().loadIcon(activity.getPackageManager());
        return d;
    }

    public void checkVersion(boolean useTimeStamp) {
        File tmpFile = new File(activity.getExternalCacheDir(), "anilab-latest.apk");
        if (tmpFile.exists()) {
            tmpFile.delete();
        }
        mMode = MODE_CHECK_VERSION;
        String versionContentUrl = getVersionContentUrl();
        if (versionContentUrl == null) {
            Log.e(TAG, "Please set versionContentUrl first");
            return;
        }

        if (useTimeStamp) {
            Calendar c = Calendar.getInstance();
            long currentTimeStamp = c.getTimeInMillis();
            long reminderTimeStamp = getReminderTime();
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "currentTimeStamp=" + currentTimeStamp);
                Log.v(TAG, "reminderTimeStamp=" + reminderTimeStamp);
            }

            if (currentTimeStamp > reminderTimeStamp) {
                // fire request to get update version content
                if (BuildConfig.DEBUG) {
                    Log.v(TAG, "getting update content...");
                }
                VersionContentRequest request = new VersionContentRequest(activity);
                request.execute(getVersionContentUrl());
            }
        } else {
            VersionContentRequest request = new VersionContentRequest(activity);
            request.execute(getVersionContentUrl());
        }

    }

    private void showDialog() {
        MaterialDialog dialog = new MaterialDialog.Builder(activity)
                .title(getTitle())
                .content(Html.fromHtml(getMessage(), null, getCustomTagHandler()))
                .positiveText(getUpdateNowLabel())
                .neutralText(getRemindMeLaterLabel())
                .negativeText(getIgnoreThisVersionLabel())
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        updateNow(getUpdateUrl());
                        super.onPositive(dialog);
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        ignoreThisVersion();
                        super.onNegative(dialog);
                    }

                    @Override
                    public void onNeutral(MaterialDialog dialog) {
                        remindMeLater(getReminderTimer());
                        super.onNeutral(dialog);
                    }
                })
                .build();
        if (activity != null && !activity.isFinishing()) {
            dialog.show();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.winsontan520.wversionmanagertest.IWVersionManager#getUpdateNowLabel()
     */
    @Override
    public String getUpdateNowLabel() {
        return updateNowLabel != null ? updateNowLabel : "Update now";
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.winsontan520.wversionmanagertest.IWVersionManager#setUpdateNowLabel
     * (java.lang.String)
     */
    @Override
    public void setUpdateNowLabel(String updateNowLabel) {
        this.updateNowLabel = updateNowLabel;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.winsontan520.wversionmanagertest.IWVersionManager#getRemindMeLaterLabel
     * ()
     */
    @Override
    public String getRemindMeLaterLabel() {
        return remindMeLaterLabel != null ? remindMeLaterLabel : "Remind me later";
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.winsontan520.wversionmanagertest.IWVersionManager#setRemindMeLaterLabel
     * (java.lang.String)
     */
    @Override
    public void setRemindMeLaterLabel(String remindMeLaterLabel) {
        this.remindMeLaterLabel = remindMeLaterLabel;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.winsontan520.wversionmanagertest.IWVersionManager#
     * getIgnoreThisVersionLabel()
     */
    @Override
    public String getIgnoreThisVersionLabel() {
        return ignoreThisVersionLabel != null ? ignoreThisVersionLabel : "Ignore this version";
    }

    /*
     * (non-Javadoc)
     *
     * @see com.winsontan520.wversionmanagertest.IWVersionManager#
     * setIgnoreThisVersionLabel(java.lang.String)
     */
    @Override
    public void setIgnoreThisVersionLabel(String ignoreThisVersionLabel) {
        this.ignoreThisVersionLabel = ignoreThisVersionLabel;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.winsontan520.wversionmanagertest.IWVersionManager#setIcon(android
     * .graphics.drawable.Drawable)
     */
    @Override
    public void setIcon(Drawable icon) {
        this.icon = icon;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.winsontan520.wversionmanagertest.IWVersionManager#setTitle(java.lang
     * .String)
     */
    @Override
    public void setTitle(String title) {
        this.title = title;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.winsontan520.wversionmanagertest.IWVersionManager#setMessage(java
     * .lang.String)
     */
    @Override
    public void setMessage(String message) {
        this.message = message;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.winsontan520.wversionmanagertest.IWVersionManager#getMessage()
     */
    @Override
    public String getMessage() {
        String defaultMessage = null;
        switch (mMode) {
            case MODE_CHECK_VERSION:
                defaultMessage = "What's new in this version";
                break;
            case MODE_ASK_FOR_RATE:
                defaultMessage = "Please rate us!";
                break;
        }

        return message != null ? message : defaultMessage;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.winsontan520.wversionmanagertest.IWVersionManager#getTitle()
     */
    @Override
    public String getTitle() {
        String defaultTitle = null;
        switch (mMode) {
            case MODE_CHECK_VERSION:
                defaultTitle = "New Update Available";
                break;
            case MODE_ASK_FOR_RATE:
                defaultTitle = "Rate this app";
                break;
        }
        return title != null ? title : defaultTitle;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.winsontan520.wversionmanagertest.IWVersionManager#getIcon()
     */
    @Override
    public Drawable getIcon() {
        return icon != null ? icon : getDefaultAppIcon();
    }

    /*
     * (non-Javadoc)
     *
     * @see com.winsontan520.wversionmanagertest.IWVersionManager#getUpdateUrl()
     */
    @Override
    public String getUpdateUrl() {
        return updateUrl != null ? updateUrl : getGooglePlayStoreUrl();
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.winsontan520.wversionmanagertest.IWVersionManager#setUpdateUrl(java
     * .lang.String)
     */
    @Override
    public void setUpdateUrl(String updateUrl) {
        this.updateUrl = updateUrl;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.winsontan520.wversionmanagertest.IWVersionManager#getVersionContentUrl
     * ()
     */
    @Override
    public String getVersionContentUrl() {
        return versionContentUrl;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.winsontan520.wversionmanagertest.IWVersionManager#setVersionContentUrl
     * (java.lang.String)
     */
    @Override
    public void setVersionContentUrl(String versionContentUrl) {
        this.versionContentUrl = versionContentUrl;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.winsontan520.wversionmanagertest.IWVersionManager#setVersionContentUrl
     * (java.lang.String)
     */
    @Override
    public int getReminderTimer() {
        return reminderTimer > 0 ? reminderTimer : (1 * 60); // default value 60
        // minutes
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.winsontan520.wversionmanagertest.IWVersionManager#setReminderTimer
     * (int)
     */
    @Override
    public void setReminderTimer(int minutes) {
        if (minutes > 0) {
            reminderTimer = minutes;
        }
    }

    private void updateNow(String url) {
        if (url != null) {
//            if (url.endsWith("apk")) {

            ProgressDialog mProgressDialog;
            // instantiate it within the onCreate method
            mProgressDialog = new ProgressDialog(activity);
            mProgressDialog.setMessage(activity.getString(R.string.download_new_version));
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setCancelable(true);
            final DownloadApkTask task = new DownloadApkTask(activity, mProgressDialog);
            task.setListener(new DownloadApkTask.DownloadTaskListener() {
                @Override
                public void downloadComplete(DownloadApkTask task) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.fromFile(new File(task.getFilePath())), "application/vnd.android.package-archive");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);

                }
            });
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    task.cancel(true);
                }
            });
            task.execute(url);
            /*} else {
                try {
                    Uri uri = Uri.parse(url);
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    activity.startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "is update url correct?" + e);
                }
            }*/
        }

    }

    private void remindMeLater(int reminderTimer) {
        Calendar c = Calendar.getInstance();
        long currentTimeStamp = c.getTimeInMillis();

        c.add(Calendar.MINUTE, reminderTimer);
        long reminderTimeStamp = c.getTimeInMillis();

        if (BuildConfig.DEBUG) {
            Log.v(TAG, "currentTimeStamp=" + currentTimeStamp);
            Log.v(TAG, "reminderTimeStamp=" + reminderTimeStamp);
        }

        setReminderTime(reminderTimeStamp);
    }

    private void setReminderTime(long reminderTimeStamp) {
        PreferenceManager.getDefaultSharedPreferences(activity).edit().putLong(PREF_REMINDER_TIME, reminderTimeStamp)
                .commit();
    }

    private long getReminderTime() {
        return PreferenceManager.getDefaultSharedPreferences(activity).getLong(PREF_REMINDER_TIME, 0);
    }

    private void ignoreThisVersion() {
        PreferenceManager.getDefaultSharedPreferences(activity).edit().putInt(PREF_IGNORE_VERSION_CODE, mVersionCode)
                .commit();
    }

    private String getGooglePlayStoreUrl() {
        String id = activity.getApplicationInfo().packageName; // current google
        // play is using
        // package name
        // as id
        return "market://details?id=" + id;
    }

    private class AlertDialogButtonListener implements DialogInterface.OnClickListener {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case AlertDialog.BUTTON_POSITIVE:
                    updateNow(getUpdateUrl());
                    break;
                case AlertDialog.BUTTON_NEUTRAL:
                    remindMeLater(getReminderTimer());
                    break;
                case AlertDialog.BUTTON_NEGATIVE:
                    ignoreThisVersion();
                    break;
            }
        }
    }

    class VersionContentRequest extends AsyncTask<String, Void, String> {
        Context context;
        int statusCode;

        public VersionContentRequest(Context context) {
            this.context = context;
        }

        @Override
        protected String doInBackground(String... uri) {
            String responseBody = null;
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(uri[0])
                        .build();
                Response response = null;

                response = client.newCall(request).execute();
                statusCode = response.code();
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Unexpected code " + response);
                    throw new IOException("Unexpected code " + response);
                }

                responseBody = response.body().string();

            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
            return responseBody;
        }

        @Override
        protected void onPostExecute(String result) {
            mVersionCode = 0;
            String content = null;
            if (statusCode != 200) {
                Log.e(TAG, "Response invalid. status code = " + statusCode);
                if (mOnReceiveListener != null) {
                    mOnReceiveListener.onReceive(statusCode, result);
                }
            } else {
                try {
                    if (!result.startsWith("{")) { // for response who append
                        // with unknown char
                        result = result.substring(1);
                    }
                    mResult = result;
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "status = " + statusCode);
                        Log.d(TAG, "result = " + mResult);
                    }

                    // show default dialog if no listener is set OR return true
                    if (mOnReceiveListener == null || mOnReceiveListener.onReceive(statusCode, result)) {
                        // json format from server:
                        JSONObject json = (JSONObject) new JSONTokener(mResult).nextValue();
                        mVersionCode = json.optInt("version_code");
                        content = json.optString("content");

                        int currentVersionCode = getCurrentVersionCode();
                        if (currentVersionCode < mVersionCode) {
                            // new versionCode will always higher than
                            // currentVersionCode
                            if (mVersionCode != getIgnoreVersionCode()) {
                                // set dialog message
                                setMessage(content);

                                // show update dialog
                                showDialog();
                            }
                        }
                    }

                } catch (JSONException e) {
                    Log.e(TAG, "is your server response have valid json format?");
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            }
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.winsontan520.wversionmanagertest.IWVersionManager#getCurrentVersionCode
     * ()
     */
    @Override
    public int getCurrentVersionCode() {
        int currentVersionCode = 0;
        currentVersionCode = Utils.getVersionCode(activity);
        return currentVersionCode;
    }

    @Override
    public int getIgnoreVersionCode() {
        return PreferenceManager.getDefaultSharedPreferences(activity).getInt(PREF_IGNORE_VERSION_CODE, 1);
    }

    @Override
    public CustomTagHandler getCustomTagHandler() {
        return customTagHandler;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.winsontan520.wversionmanagertest.IWVersionManager#setCustomTagHandler
     * (com.winsontan520.wversionmanagertest.WVersionManager.CustomTagHandler)
     */
    @Override
    public void setCustomTagHandler(CustomTagHandler customTagHandler) {
        this.customTagHandler = customTagHandler;
    }

    public boolean isDialogCancelable() {
        return mDialogCancelable;
    }

    public void setDialogCancelable(boolean dialogCancelable) {
        mDialogCancelable = dialogCancelable;
    }

    public void askForRate() {
        mMode = MODE_ASK_FOR_RATE;
        showDialog();
    }

    public String getAskForRatePositiveLabel() {
        return mAskForRatePositiveLabel == null ? "OK" : mAskForRatePositiveLabel;
    }

    public void setAskForRatePositiveLabel(String askForRatePositiveLabel) {
        mAskForRatePositiveLabel = askForRatePositiveLabel;
    }

    public String getAskForRateNegativeLabel() {
        return mAskForRateNegativeLabel == null ? "Not now" : mAskForRateNegativeLabel;
    }

    public void setAskForRateNegativeLabel(String askForRateNegativeLabel) {
        mAskForRateNegativeLabel = askForRateNegativeLabel;
    }

    @Override
    public void setOnReceiveListener(OnReceiveListener listener) {
        this.mOnReceiveListener = listener;
    }

}