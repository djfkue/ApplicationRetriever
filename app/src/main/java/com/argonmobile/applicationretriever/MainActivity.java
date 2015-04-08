package com.argonmobile.applicationretriever;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;


public class MainActivity extends ActionBarActivity implements View.OnClickListener{

    public static final String TAG = "MainActivity";

    public static final int FETCH_PACKAGE_SIZE_COMPLETED = 100;
    public static final int ALL_PACKAGE_SIZE_COMPLETED = 200;

    long mTotalPackageCacheSize = 0;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case FETCH_PACKAGE_SIZE_COMPLETED:
                    if (mTotalPackageCacheSize > 0) {
                        long size = (mTotalPackageCacheSize / 1024 / 1024);
                        ((TextView) findViewById(R.id.textView)).setText("Cache Size : " + size + " MB");
                    }
                    mAppAdapter.notifyDataSetChanged();
                    break;
                case ALL_PACKAGE_SIZE_COMPLETED:
                    if (null != mProgressDialog)
                        if (mProgressDialog.isShowing())
                            mProgressDialog.dismiss();
                    mAppAdapter.notifyDataSetChanged();
                    break;
                default:
                    break;
            }

        }

    };

    ProgressDialog mProgressDialog;

    AppDetails mAppDetails;
    ArrayList<AppDetails.PackageInfoStruct> mPackageInfoList = new ArrayList<>();

    private ListView mCacheListView;
    private AppAdapter mAppAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.btn_scan).setOnClickListener(this);
        findViewById(R.id.btn_clear).setOnClickListener(this);
        mCacheListView = (ListView) findViewById(R.id.app_list);
        mAppAdapter = new AppAdapter(this, R.layout.activity_main);
        mCacheListView.setAdapter(mAppAdapter);
    }

    private void getPackageSize() {
        mAppDetails = new AppDetails(this);
        mPackageInfoList = mAppDetails.getPackages();
        if (mPackageInfoList == null)
            return;
        for (int m = 0; m < mPackageInfoList.size(); m++) {
            PackageManager pm = getPackageManager();
            Method getPackageSizeInfo;
            try {
                getPackageSizeInfo = pm.getClass().getMethod(
                        "getPackageSizeInfo", String.class,
                        IPackageStatsObserver.class);
                getPackageSizeInfo.invoke(pm, mPackageInfoList.get(m).mAppPackageName,
                        new CachePackageStateObserver());
            } catch (SecurityException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }

        }
        mHandler.sendEmptyMessage(ALL_PACKAGE_SIZE_COMPLETED);
        Log.v(TAG, "app size:" + " " + mPackageInfoList.size());

    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_scan) {
            mTotalPackageCacheSize = 0;
            showProgress("Calculating Cache Size..!!!");
            /**
             * You can also use async task
             * */
            new Thread(new Runnable() {

                @Override
                public void run() {
                    getPackageSize();
                }
            }).start();
        }
        if (v.getId() == R.id.btn_clear) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    clearCache();
                }
            }).start();
        }
    }

    private void clearCache() {
        PackageManager  pm = getPackageManager();
        // Get all methods on the PackageManager
        Method[] methods = pm.getClass().getDeclaredMethods();
        for (Method m : methods) {
            if (m.getName().equals("freeStorage")) {

                Log.e(TAG, "freeStorage.............");
                long time = System.currentTimeMillis();
                // Found the method I want to use
                try {
                    long desiredFreeStorage = Long.MAX_VALUE; // Request for 8GB of free space
                    m.invoke(pm, desiredFreeStorage , null);
                } catch (Exception e) {
                    // Method invocation failed. Could be a permission problem
                    e.printStackTrace();
                }
                long timeInterval = (System.currentTimeMillis() - time);

                Log.e(TAG, "freeStorage............. timeInterval: " + timeInterval);
                break;
            }
        }
    }

    private void showProgress(String message) {
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setIcon(R.drawable.ic_launcher);
        mProgressDialog.setTitle("Please Wait...");
        mProgressDialog.setMessage(message);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();

    }

    private class CachePackageStateObserver extends IPackageStatsObserver.Stub {

        @Override
        public void onGetStatsCompleted(PackageStats pStats, boolean succeeded)
                throws RemoteException {
            Log.d(TAG, "Package Size " + pStats.packageName + "");
            Log.i(TAG, "Cache Size "+ pStats.cacheSize + "");
            Log.w(TAG, "Data Size " + pStats.dataSize + "");
            mTotalPackageCacheSize = mTotalPackageCacheSize + pStats.cacheSize + pStats.externalCacheSize;
            Log.v(TAG, "Total Cache Size" + " " + mTotalPackageCacheSize);

            AppDetails.PackageInfoStruct packageInfoStruct = findPackageInfo(pStats.packageName);
            packageInfoStruct.cacheSize = pStats.cacheSize + pStats.externalCacheSize;
            mHandler.sendEmptyMessage(FETCH_PACKAGE_SIZE_COMPLETED);
        }

    }

    private AppDetails.PackageInfoStruct findPackageInfo(String packageName) {
        for (AppDetails.PackageInfoStruct packageInfoStruct : mPackageInfoList) {
            if (packageInfoStruct.mAppPackageName.equals(packageName)) {
                return packageInfoStruct;
            }
        }
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    class AppAdapter extends ArrayAdapter<AppDetails.PackageInfoStruct> {

        public AppAdapter(Context context, int resource) {
            super(context, resource);
        }

        @Override
        public int getCount() {
            return mPackageInfoList.size();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.application_info, parent, false);
            }
            ImageView appIcon = (ImageView)convertView.findViewById(R.id.app_icon);
            TextView appName = (TextView) convertView.findViewById(R.id.app_name);
            TextView packageName = (TextView) convertView.findViewById(R.id.package_name);
            TextView packageSize = (TextView) convertView.findViewById(R.id.cache_size);

            AppDetails.PackageInfoStruct packageInfoStruct = mPackageInfoList.get(position);

            appIcon.setImageDrawable(packageInfoStruct.icon);
            appName.setText(packageInfoStruct.mAppName);
            packageName.setText(packageInfoStruct.mAppPackageName);
            packageSize.setText("Cache Size: " + packageInfoStruct.cacheSize / 1024.0f + " K");

            return convertView;
        }
    }
}
