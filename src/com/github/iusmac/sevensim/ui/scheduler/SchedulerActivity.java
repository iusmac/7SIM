package com.github.iusmac.sevensim.ui.scheduler;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.view.Menu;
import android.view.MenuItem;

import androidx.lifecycle.ViewModelProvider;

import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.Utils;
import com.github.iusmac.sevensim.telephony.Subscription;
import com.github.iusmac.sevensim.telephony.Subscriptions;
import com.github.iusmac.sevensim.ui.components.CollapsingToolbarBaseActivity;

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

    private Logger mLogger;
    private SchedulerViewModel mViewModel;

    private Subscription mSubscription;

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.scheduler, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        menu.findItem(R.id.scheduler_reset).setEnabled(mViewModel.schedulerExists());
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
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();

        mLogger = mLoggerFactory.create(getClass().getSimpleName());

        final Bundle extras = intent.getExtras();
        if (extras == null) {
            throw new IllegalArgumentException("Extra Bundle is NULL!");
        }

        mSubscription = Utils.getParcelable(extras, EXTRA_SUBSCRIPTION, Subscription.class);

        mLogger.d("onCreate() : (extra) %s.", mSubscription);

        super.setTitle(mSubscription.getSimName());

        final ViewModelProvider vmp;
        if (savedInstanceState == null) {
            vmp = new ViewModelProvider(this, SchedulerViewModel.getFactory(mViewModelFactory,
                        mSubscription.getId(), sHandler.getLooper()));
            commitFragment();
        } else {
            vmp = new ViewModelProvider(this);
        }
        mViewModel = vmp.get(SchedulerViewModel.class);
    }

    private void commitFragment() {
        final int containerViewId = com.android.settingslib.widget.R.id.content_frame;
        getSupportFragmentManager().beginTransaction().add(containerViewId,
                new SchedulerFragment()).commit();
    }

    private void showResetDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.scheduler_toolbar_reset)
            .setIconAttribute(android.R.attr.alertDialogIcon)
            .setMessage(R.string.scheduler_reset_dialog_message)
            .setPositiveButton(android.R.string.ok, (dialog, id) -> mViewModel.removeScheduler())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @Override
    public void onSubscriptionsChanged() {
        sHandler.post(() -> mSubscriptions.getSubscriptionForSubId(mSubscription.getId())
                .ifPresent((sub) -> runOnUiThread(() -> super.setTitle(sub.getSimName()))));
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
    }
}
