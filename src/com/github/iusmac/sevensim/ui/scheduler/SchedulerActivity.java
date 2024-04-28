package com.github.iusmac.sevensim.ui.scheduler;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.Utils;
import com.github.iusmac.sevensim.scheduler.SubscriptionScheduler;
import com.github.iusmac.sevensim.telephony.Subscription;
import com.github.iusmac.sevensim.telephony.Subscriptions;
import com.github.iusmac.sevensim.ui.components.CollapsingToolbarBaseActivity;
import com.github.iusmac.sevensim.ui.components.toolbar.ToolbarDecorator;

import dagger.hilt.android.AndroidEntryPoint;

import javax.inject.Inject;

@AndroidEntryPoint(CollapsingToolbarBaseActivity.class)
public final class SchedulerActivity extends Hilt_SchedulerActivity
    implements Subscriptions.OnSubscriptionsChangedListener {

    private static final Handler sHandler;
    static {
        final HandlerThread handlerThread = new HandlerThread(
                SchedulerActivity.class.getSimpleName() + "ViewModelThread",
                Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();
        sHandler = new Handler(handlerThread.getLooper());
    }

    public static final String EXTRA_SUBSCRIPTION = "extra_subscription";

    @Inject
    Logger.Factory mLoggerFactory;

    @Inject
    SchedulerViewModel.Factory mViewModelFactory;

    @Inject
    Subscriptions mSubscriptions;

    @Inject
    SubscriptionScheduler mSubscriptionScheduler;

    private Logger mLogger;

    private Subscription mSubscription;
    private final Object mSubscriptionsChangedToken = new Object();
    private boolean mSubscriptionsChangedListenerInitialized;

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.scheduler, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        menu.findItem(R.id.scheduler_reset).setEnabled(getViewModel().schedulerExists());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (android.R.id.home == itemId) {
            onBackPressed();
            return true;
        } else if (R.id.scheduler_reset == itemId) {
            showResetDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public ViewModel onCreateViewModel() {
        // Make Dagger instantiate @Inject fields prior to the ViewModel creation
        inject();

        final Intent intent = getIntent();

        mLogger = mLoggerFactory.create(getClass().getSimpleName());

        final Bundle extras = intent.getExtras();
        if (extras == null) {
            throw new IllegalArgumentException("Extra Bundle is NULL!");
        }

        mSubscription = Utils.getParcelable(extras, EXTRA_SUBSCRIPTION, Subscription.class);

        final ViewModelProvider.Factory vmpFactory =
            SchedulerViewModel.getFactory(mViewModelFactory, mSubscription.getId(),
                    sHandler.getLooper());
        return new ViewModelProvider(this, vmpFactory).get(SchedulerViewModel.class);
    }

    @Override
    public SchedulerViewModel getViewModel() {
        return (SchedulerViewModel) super.getViewModel();
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mLogger = mLoggerFactory.create(getClass().getSimpleName());

        mLogger.d("onCreate() : (extra) %s.", mSubscription);

        super.setTitle(mSubscription.getSimName());
        final ToolbarDecorator toolbarDecorator = getToolbarDecorator();
        if (toolbarDecorator.isCollapsingToolbarSupported()) {
            toolbarDecorator.setCollapsingSubtitleImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        } else {
            toolbarDecorator.setSubtitleMarqueeRepeatLimit(-1);
        }
        getViewModel().getNextUpcomingScheduleSummary().observe(this, (summary) ->
                setSubtitle(summary));

        if (savedInstanceState == null) {
            commitFragment();
        }
    }

    private void commitFragment() {
        final int containerViewId = com.android.settingslib.collapsingtoolbar.R.id.content_frame;
        getSupportFragmentManager().beginTransaction().add(containerViewId,
                new SchedulerFragment()).commit();
    }

    private void showResetDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.scheduler_toolbar_reset)
            .setIconAttribute(android.R.attr.alertDialogIcon)
            .setMessage(R.string.scheduler_reset_dialog_message)
            .setPositiveButton(android.R.string.ok, (dialog, id) ->
                    getViewModel().removeScheduler())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @Override
    public void onSubscriptionsChanged() {
        mLogger.v("onSubscriptionsChanged().");

        // Debouncing
        final long delayMillis;
        if (mSubscriptionsChangedListenerInitialized) {
            delayMillis = 300;
            sHandler.removeCallbacksAndMessages(mSubscriptionsChangedToken);
        } else {
            delayMillis = 0;
            mSubscriptionsChangedListenerInitialized = true;
        }

        sHandler.postDelayed(() -> mSubscriptions.getSubscriptionForSubId(mSubscription.getId())
                .ifPresent((sub) -> runOnUiThread(() -> super.setTitle(sub.getSimName()))),
                mSubscriptionsChangedToken, delayMillis);
        sHandler.postDelayed(() -> getViewModel().refreshNextUpcomingScheduleSummary(),
                mSubscriptionsChangedToken, delayMillis);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mSubscriptions.addOnSubscriptionsChangedListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mSubscriptions.removeOnSubscriptionsChangedListener(this);
        sHandler.removeCallbacksAndMessages(mSubscriptionsChangedToken);
    }
}
