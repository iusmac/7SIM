package com.github.iusmac.sevensim.ui.sim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.telephony.Subscriptions;
import com.github.iusmac.sevensim.ui.sim.SimListViewModel.Factory;

import dagger.hilt.android.AndroidEntryPoint;

import javax.inject.Inject;

@AndroidEntryPoint(CollapsingToolbarBaseActivity.class)
public class SimListActivity extends Hilt_SimListActivity
    implements Subscriptions.OnSubscriptionsChangedListener {

    private final static Handler sHandler;
    static {
        final HandlerThread handlerThread = new HandlerThread(SimListActivity.class.getSimpleName()
                + "ViewModelThread", Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();
        sHandler = Handler.createAsync(handlerThread.getLooper());
    }

    @Inject
    Logger.Factory mLoggerFactory;

    @Inject
    Factory mSimListViewModelFactory;

    @Inject
    Subscriptions mSubscriptions;

    private final IntentReceiver mIntentReceiver = new IntentReceiver();

    private Logger mLogger;
    private SimListViewModel mViewModel;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mLogger = mLoggerFactory.create(getClass().getSimpleName());

        mLogger.d("onCreate().");

        mViewModel = new ViewModelProvider(this,
                SimListViewModel.getFactory(mSimListViewModelFactory,
                    sHandler.getLooper())).get(SimListViewModel.class);

        if (savedInstanceState == null) {
            commitFragment();
        }
    }

    private void commitFragment() {
        mLogger.d("commitFragment().");

        final int containerViewId = com.android.settingslib.widget.R.id.content_frame;
        getSupportFragmentManager().beginTransaction().add(containerViewId,
                new SimListFragment()).commit();
    }

    @Override
    public void onSubscriptionsChanged() {
        mLogger.v("onSubscriptionsChanged().");

        sHandler.post(mViewModel::refreshSimEntries);
    }

    @Override
    protected void onResume() {
        super.onResume();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        ContextCompat.registerReceiver(this, mIntentReceiver, filter,
                ContextCompat.RECEIVER_EXPORTED);

        mSubscriptions.addOnSubscriptionsChangedListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mSubscriptions.removeOnSubscriptionsChangedListener(this);
        unregisterReceiver(mIntentReceiver);
    }

    private final class IntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();

            mLogger.d("onReceive() : action=" + action);

            switch (action) {
                case Intent.ACTION_LOCALE_CHANGED:
                    // Refresh SIM entries to regenerate locale-sensitive data
                    sHandler.post(mViewModel::refreshSimEntries);
                    break;

                case Intent.ACTION_TIMEZONE_CHANGED:
                case Intent.ACTION_TIME_CHANGED:
                    // Refresh SIM entries to regenerate time-sensitive data
                    sHandler.post(mViewModel::refreshSimEntries);
                    break;
            }
        }
    }
}