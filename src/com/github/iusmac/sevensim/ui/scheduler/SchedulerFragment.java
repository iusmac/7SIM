package com.github.iusmac.sevensim.ui.scheduler;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.MenuCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settingslib.widget.BannerMessagePreference;
import com.android.settingslib.widget.LayoutPreference;

import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.Utils;
import com.github.iusmac.sevensim.scheduler.DayOfWeek;
import com.github.iusmac.sevensim.scheduler.SubscriptionScheduleEntity;
import com.github.iusmac.sevensim.telephony.TelephonyUtils;
import com.github.iusmac.sevensim.ui.AuthenticationPromptActivity;
import com.github.iusmac.sevensim.ui.UiUtils;
import com.github.iusmac.sevensim.ui.components.EditTextDialogFragment;
import com.github.iusmac.sevensim.ui.components.ItemAdapter;
import com.github.iusmac.sevensim.ui.components.ItemAnimator;
import com.github.iusmac.sevensim.ui.components.TimePickerDialogFragment;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import dagger.hilt.android.AndroidEntryPoint;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;

import javax.inject.Inject;

@AndroidEntryPoint(PreferenceFragmentCompat.class)
public final class SchedulerFragment extends Hilt_SchedulerFragment
    implements FragmentResultListener, ScheduleItemViewHolderClickHandler {

    private static final long INVALID_SCHEDULE_ID = RecyclerView.NO_ID;

    private static Comparator<ScheduleItemHolder> SCHEDULE_ITEM_HOLDER_SORTER =
        new Comparator<>() {
            @Override
            public int compare(final ScheduleItemHolder holder1,
                    final ScheduleItemHolder holder2) {

                final SubscriptionScheduleEntity item1 = holder1.item;
                final SubscriptionScheduleEntity item2 = holder2.item;
                int result;
                // Sort in ascending order by the days of the week first, then by the time
                if ((result = item1.getDaysOfWeek().compareTo(item2.getDaysOfWeek())) == 0
                        && (result = item1.getTime().compareTo(item2.getTime())) == 0) {
                    // Item are the same, so sort in descending order by ID to make the last added
                    // item appear higher in the list
                    result = -Long.compare(holder1.itemId, holder2.itemId);
                }
                return result;
            }
        };

    private static final String ACTION_AUTH_HANDLE_ON_PIN_CHANGED =
        "action_auth_handle_on_pin_changed";

    private static final String ACTION_AUTH_HANDLE_ON_ENABLED_STATE_CHANGED =
        "action_auth_handle_on_enabled_state_changed";

    private static final String ACTION_AUTH_HANDLE_ON_SUBSCRIPTION_ENABLED_STATE_CHANGED =
        "action_auth_handle_on_subscription_enabled_state_changed";

    private static final String ACTION_AUTH_HANDLE_ON_DAYS_OF_WEEK_CHANGED =
        "action_auth_handle_on_days_of_week_changed";

    private static final String ACTION_AUTH_HANDLE_ON_TIME_PICKED =
        "action_auth_handle_on_time_picked";

    private static final String ACTION_AUTH_HANDLE_ON_SCHEDULE_DELETED =
        "action_auth_handle_on_schedule_deleted";

    private static final String EXTRA_PIN = "pin";
    private static final String EXTRA_ENABLED = "enabled";
    private static final String EXTRA_SIM_ENABLED = "sim_enabled";
    private static final String EXTRA_DAY_OF_WEEK = "day_of_week";
    private static final String EXTRA_DAY_OF_WEEK_ENABLED = "day_of_week_enabled";
    private static final String EXTRA_TIME = "time";

    private static final String SAVED_PIN_POPUP_VISIBLE = "pinPopupVisible";
    private static final String SAVED_EXPANDED_SCHEDULE_ID = "expandedScheduleId";
    private static final String SAVED_EXPANDED_SCHEDULE_STATE = "expandedScheduleState";
    private static final String SAVED_SELECTED_SCHEDULE_ID = "selectedScheduleId";

    private static final String PIN_PROMPT_RESULT_REQUEST_KEY = "pin_prompt_result";
    private static final String EDIT_LABEL_RESULT_REQUEST_KEY = "edit_label_result";
    private static final String TIME_PICKER_RESULT_REQUEST_KEY = "time_picker_result";

    @Inject
    Logger.Factory mLoggerFactory;

    @Inject
    ExpandedScheduleViewHolder.Factory mExpandedScheduleViewHolderFactory;

    private Logger mLogger;
    private SchedulerViewModel mViewModel;
    private final ActivityResultLauncher<Intent> mAuthenticationPromptLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                this::onAuthResult);
    private boolean mPinPopupMenuVisible;
    private long mExpandedScheduleId = INVALID_SCHEDULE_ID,
            mSelectedScheduleId = INVALID_SCHEDULE_ID,
            mScrollToScheduleId = INVALID_SCHEDULE_ID;
    private Bundle mExpandedScheduleSavedState;
    private SubscriptionScheduleEntity mSelectedSchedule;

    private RecyclerView mRecyclerView;
    private ConcatAdapter mConcatAdapter;
    private final ItemAdapter<ScheduleItemHolder> mItemAdapter =
        new ItemAdapter<>(ScheduleItemHolder.class, SCHEDULE_ITEM_HOLDER_SORTER);

    private LayoutPreference mEmptyViewPref;
    private FloatingActionButton mPinFab;
    private FloatingActionButton mAddFab;
    private PopupMenu mPinPopupMenu;

    private void onAuthResult(final ActivityResult result) {
        mLogger.d("onAuthResult(result=%s).", result);

        if (result.getResultCode() != Activity.RESULT_OK) {
            if (mSelectedSchedule != null) {
                // Authentication was unsuccessful or aborted, but the selected schedule contains
                // some changes made by the user, such as on/off of the toggle switch, so we need to
                // refresh the schedule view to use the actual data
                refreshScheduleItem(mSelectedSchedule.getId());
            }
            return;
        }

        final Intent data = result.getData();
        final String action = data.getAction() != null ? data.getAction() : "";
        switch (action) {
            case ACTION_AUTH_HANDLE_ON_ENABLED_STATE_CHANGED:
                handleOnEnabledStateChanged(data.getBooleanExtra(EXTRA_ENABLED, false));
                break;

                case ACTION_AUTH_HANDLE_ON_SUBSCRIPTION_ENABLED_STATE_CHANGED:
                handleOnSubscriptionEnabledStateChanged(data
                        .getBooleanExtra(EXTRA_SIM_ENABLED, false));
                break;

            case ACTION_AUTH_HANDLE_ON_DAYS_OF_WEEK_CHANGED:
                handleOnDayOfWeekChangedChanged(data.getIntExtra(EXTRA_DAY_OF_WEEK, 0),
                        data.getBooleanExtra(EXTRA_DAY_OF_WEEK_ENABLED, false));
                break;

            case ACTION_AUTH_HANDLE_ON_TIME_PICKED:
                handleOnTimePicked(data.getStringExtra(EXTRA_TIME));
                break;

            case ACTION_AUTH_HANDLE_ON_SCHEDULE_DELETED:
                handleOnScheduleDeleted();
                break;

            case ACTION_AUTH_HANDLE_ON_PIN_CHANGED:
                mViewModel.handleOnPinChanged(data.getStringExtra(EXTRA_PIN));
                break;

            default: mLogger.wtf("onAuthResult(result=%s) : unhandled action: %s.", result, action);
        }
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);

        mLogger = mLoggerFactory.create(getClass().getSimpleName());
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        mViewModel = ((SchedulerActivity) requireActivity()).getViewModel();

        addPreferencesFromResource(R.xml.scheduler_preferences);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {

        final RelativeLayout fabContainer = (RelativeLayout)
            inflater.inflate(R.layout.scheduler_fabs, null, false);
        mPinFab = fabContainer.findViewById(R.id.fab_pin);
        mAddFab = fabContainer.findViewById(R.id.fab_add);

        final ViewGroup.MarginLayoutParams marginLp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        // Add FAB container to an outer container that varies depending on the Android version
        final ViewParent parent = container.getParent();
        if (parent instanceof CoordinatorLayout) {
            final CoordinatorLayout.LayoutParams lp = new CoordinatorLayout.LayoutParams(marginLp);
            lp.gravity = Gravity.BOTTOM;
            ((ViewGroup) parent).addView(fabContainer, lp);
        } else {
            final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(marginLp);
            lp.gravity = Gravity.BOTTOM;
            container.addView(fabContainer, lp);
        }

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final FragmentManager fm = getParentFragmentManager();
        fm.setFragmentResultListener(PIN_PROMPT_RESULT_REQUEST_KEY, getViewLifecycleOwner(), this);
        fm.setFragmentResultListener(EDIT_LABEL_RESULT_REQUEST_KEY, getViewLifecycleOwner(), this);
        fm.setFragmentResultListener(TIME_PICKER_RESULT_REQUEST_KEY, getViewLifecycleOwner(), this);

        setupPinErrorPref();
        setupEmptyViewPref();
        setupScheduleList();
        setupPinFab();
        setupAddFab();
    }

    private void setupPinErrorPref() {
        final BannerMessagePreference pinErrorPref = ((BannerMessagePreference)
                findPreference(getString(R.string.scheduler_pin_error_key)))
            .setPositiveButtonText(R.string.scheduler_pin_banner_enter_pin_code_button_text)
            .setPositiveButtonOnClickListener((view) -> showPinPromptDialog());

        mViewModel.getPinTaskLock().observe(getViewLifecycleOwner(), (isLockHeld) ->
                pinErrorPref.setEnabled(!isLockHeld));

        mViewModel.getPinErrorMessage().observe(getViewLifecycleOwner(), (pinErrorMessage) -> {
            pinErrorMessage.ifPresent((error) -> {
                pinErrorPref.setTitle(error.title);
                pinErrorPref.setSummary(error.reason);
            });
            mRecyclerView.getItemAnimator().isRunning(() ->
                        pinErrorPref.setVisible(pinErrorMessage.isPresent()));
        });
    }

    private void setupEmptyViewPref() {
        mEmptyViewPref = findPreference(getString(R.string.scheduler_empty_view_key));
        mEmptyViewPref.findViewById(R.id.body).setOnClickListener((v) -> startCreatingSchedule());
    }

    private void setupScheduleList() {
        final Context context = requireContext();
        mItemAdapter.setHasStableIds();
        mItemAdapter.withViewTypes(CollapsedScheduleViewHolder.getItemViewHolderFactory(context),
                /*listener=*/ null, CollapsedScheduleViewHolder.VIEW_TYPE);
        mItemAdapter.withViewTypes(ExpandedScheduleViewHolder.getItemViewHolderFactory(context,
                    mExpandedScheduleViewHolderFactory), /*listener=*/ null,
                ExpandedScheduleViewHolder.VIEW_TYPE);
        mItemAdapter.setOnItemChangedListener(new ItemAdapter.OnItemChangedListener() {
            @Override
            public void onItemChanged(final ItemAdapter.ItemHolder<?> holder) {
                if (((ScheduleItemHolder) holder).isExpanded()) {
                    if (mExpandedScheduleId != holder.itemId) {
                        // Collapse the prior expanded schedule
                        final ScheduleItemHolder itemHolder =
                            mItemAdapter.findItemById(mExpandedScheduleId);
                        if (itemHolder != null) {
                            itemHolder.collapse();
                        }
                        // Record the freshly expanded item
                        mExpandedScheduleId = holder.itemId;
                        scrollToSchedule(holder.itemId);
                    }
                } else if (mExpandedScheduleId == holder.itemId) {
                    // The expanded schedule is now collapsed so update the tracking id
                    mExpandedScheduleId = INVALID_SCHEDULE_ID;
                }
            }

            @Override
            public void onItemChanged(ItemAdapter.ItemHolder<?> holder, Object payload) {
                /* No additional work to do */
            }
        });
        mItemAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeRemoved(final int positionStart, final int itemCount) {
                // Show or hide the empty view as appropriate
                if (mItemAdapter.getItemCount() == 0) {
                    mRecyclerView.post(() -> mRecyclerView.getItemAnimator().isRunning(() ->
                                mEmptyViewPref.setVisible(mItemAdapter.getItemCount() == 0)));
                }
            }

            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                // Scroll to the position where a schedule is moving. This happens after the user
                // edited a schedule, which triggered item rearrange due to changed sorting criteria
                smoothScrollTo(getScheduleGlobalPosition(toPosition));
            }
        });
        mConcatAdapter.addAdapter(mItemAdapter);

        mViewModel.getSchedules().observe(getViewLifecycleOwner(), (scheduleList) ->
                scheduleList.ifPresent((schedules) -> {
                    if (!schedules.isEmpty()) {
                        final ScheduleItemHolder[] items = schedules.stream()
                            .map((schedule) -> new ScheduleItemHolder(schedule, this))
                            .toArray(ScheduleItemHolder[]::new);
                        addAdapterItems(items);
                    } else {
                        mEmptyViewPref.setVisible(true);
                    }
                }));

        mViewModel.getScheduleAddedListener().observe(getViewLifecycleOwner(), (schedule) -> {
            if (schedule != null && mItemAdapter.findItemById(schedule.getId()) == null) {
                // Be ready to expand & scroll to this newly added schedule later
                mScrollToScheduleId = schedule.getId();
                mEmptyViewPref.setVisible(false);
                mRecyclerView.post(() -> addAdapterItems(new ScheduleItemHolder(schedule, this)));
            }
        });
    }

    private void setupPinFab() {
        mViewModel.getPinTaskLock().observe(getViewLifecycleOwner(), (isPinTaskLockHeld) ->
                mPinFab.setEnabled(!isPinTaskLockHeld));

        mViewModel.getPinPresence().observe(getViewLifecycleOwner(), (isPresent) ->
                mPinFab.setImageState(new int[] { isPresent ? android.R.attr.state_checked : 0 },
                    /*merge=*/ true));

        mPinPopupMenu = new PopupMenu(requireActivity(), mPinFab, Gravity.NO_GRAVITY,
                /*popupStyleAttr=*/ 0, R.style.PopupMenuDefaultAnimationStyle);
        final Menu menu = mPinPopupMenu.getMenu();
        MenuCompat.setGroupDividerEnabled(menu, true);
        mPinPopupMenu.getMenuInflater().inflate(R.menu.scheduler_pin_options, menu);
        mPinPopupMenu.setOnMenuItemClickListener((menuItem) -> {
            final int itemId = menuItem.getItemId();
            if (R.id.scheduler_pin_edit_option == itemId) {
                showPinPromptDialog();
            } else if (R.id.scheduler_pin_delete_option == itemId) {
                mViewModel.removePin();
            } else {
                mLogger.wtf("Unhandled menu option: %s.", menuItem);
            }
            return true;
        });
        mPinPopupMenu.setOnDismissListener((p) -> mPinPopupMenuVisible = false);

        mPinFab.setOnClickListener((v) -> {
            if (mViewModel.isPinPresent()) {
                mPinPopupMenuVisible = true;
                mPinPopupMenu.show();
            } else {
                showPinPromptDialog();
            }
        });
    }

    private void setupAddFab() {
        mAddFab.setBackgroundTintList(requireContext()
                .getColorStateList(R.color.fab_add_background_tint_color));
        mAddFab.setOnClickListener((v) -> startCreatingSchedule());
    }

    private void startCreatingSchedule() {
        // Clear the currently selected schedule
        mSelectedSchedule = null;
        showTimePicker(LocalTime.now(ZoneId.systemDefault()));
    }

    private void showPinPromptDialog() {
        final EditTextDialogFragment dialogFragment =
            new EditTextDialogFragment(PIN_PROMPT_RESULT_REQUEST_KEY);
        dialogFragment.setTitle(getString(R.string.scheduler_pin_title));
        // Allow only numbers in the input field
        dialogFragment.setInputType(InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        // Limit the maximum PIN length as per UICC specs
        dialogFragment.setMaxInputLength(TelephonyUtils.PIN_MAX_PIN_LENGTH);
        dialogFragment.show(getParentFragmentManager(), EditTextDialogFragment.TAG);
    }

    private void handleOnTimePicked(final String time) {
        if (mViewModel.isPinPresent() && mViewModel.isAuthenticationRequired()) {
            final Bundle payload = new Bundle(1);
            payload.putString(EXTRA_TIME, time);
            authenticateAndRunAction(ACTION_AUTH_HANDLE_ON_TIME_PICKED, payload);
        } else {
            if (mSelectedSchedule != null) {
                mViewModel.handleOnTimeChanged(mSelectedSchedule, time);
                // Rearrange the schedule in the list since sorting criteria has been changed
                mItemAdapter.recalculatePositionOfItemAt(mItemAdapter
                        .getPosition(mSelectedSchedule.getId()));
                // Reflect the updated time in the selected schedule after rearrangement is done
                mRecyclerView.post(() -> mRecyclerView.getItemAnimator().isRunning(() ->
                            refreshScheduleItem(mSelectedSchedule.getId())));
            } else {
                // If mSelectedSchedule is null then we're adding a new schedule
                mViewModel.handleOnTimePicked(time);
            }
        }
    }

    private void handleOnLabelChanged(final String label) {
        mViewModel.handleOnLabelChanged(mSelectedSchedule, label);
        // Reflect the updated label text in the selected schedule
        refreshScheduleItem(mSelectedSchedule.getId());
    }

    private void handleOnEnabledStateChanged(final boolean enabled) {
        if (mViewModel.isPinPresent() && mViewModel.isAuthenticationRequired()) {
            final Bundle payload = new Bundle(1);
            payload.putBoolean(EXTRA_ENABLED, enabled);
            authenticateAndRunAction(ACTION_AUTH_HANDLE_ON_ENABLED_STATE_CHANGED, payload);
        } else {
            mViewModel.handleOnEnabledStateChanged(mSelectedSchedule, enabled);
            // Refresh the selected schedule to update the views depended on the enabled state
            refreshScheduleItem(mSelectedSchedule.getId());
        }
    }

    private void handleOnSubscriptionEnabledStateChanged(boolean enabled) {
        if (mViewModel.isPinPresent() && mViewModel.isAuthenticationRequired()) {
            final Bundle payload = new Bundle(1);
            payload.putBoolean(EXTRA_SIM_ENABLED, enabled);
            authenticateAndRunAction(ACTION_AUTH_HANDLE_ON_SUBSCRIPTION_ENABLED_STATE_CHANGED,
                    payload);
        } else {
            mViewModel.handleOnSubscriptionEnabledStateChanged(mSelectedSchedule, enabled);
            // Refresh the selected schedule to update the views depended on the enabled state
            refreshScheduleItem(mSelectedSchedule.getId());
        }
    }

    private void handleOnDayOfWeekChangedChanged(final @DayOfWeek int dayOfWeek,
            final boolean enabled) {

        if (mViewModel.isPinPresent() && mViewModel.isAuthenticationRequired()) {
            final Bundle payload = new Bundle(2);
            payload.putInt(EXTRA_DAY_OF_WEEK, dayOfWeek);
            payload.putBoolean(EXTRA_DAY_OF_WEEK_ENABLED, enabled);
            authenticateAndRunAction(ACTION_AUTH_HANDLE_ON_DAYS_OF_WEEK_CHANGED, payload);
        } else {
            mViewModel.handleOnDayOfWeekChanged(mSelectedSchedule, dayOfWeek, enabled);
            // Rearrange the schedule in the list since sorting criteria has been changed
            mItemAdapter.recalculatePositionOfItemAt(mItemAdapter
                    .getPosition(mSelectedSchedule.getId()));
            // Reflect the changes in the selected schedule after rearrangement is done
            mRecyclerView.post(() -> mRecyclerView.getItemAnimator().isRunning(() ->
                        refreshScheduleItem(mSelectedSchedule.getId())));
        }
    }

    private void handleOnScheduleDeleted() {
        if (mViewModel.isPinPresent() && mViewModel.isAuthenticationRequired()) {
            authenticateAndRunAction(ACTION_AUTH_HANDLE_ON_SCHEDULE_DELETED, /*payload=*/ null);
        } else {
            mItemAdapter.removeItemAt(mItemAdapter.getPosition(mSelectedSchedule.getId()));
            mViewModel.handleOnDeleted(mSelectedSchedule);

            // Clear the currently selected schedule
            mSelectedSchedule = null;
            mExpandedScheduleId = INVALID_SCHEDULE_ID;
        }
    }

    private void handleOnPinChanged(final String pin) {
        // Proceed only if PIN string meets the UICC specs
        if (!TelephonyUtils.isValidPin(pin)) {
            Utils.makeToast(requireContext(), getString(R.string.scheduler_pin_invalid_hint));
            return;
        }

        // Authenticate the user again to unlock the hardware-backed KeyStore for further crypto
        // operations on the provided SIM PIN code
        if (mViewModel.isAuthenticationRequired()) {
            final Bundle payload = new Bundle(1);
            payload.putString(EXTRA_PIN, pin);
            authenticateAndRunAction(ACTION_AUTH_HANDLE_ON_PIN_CHANGED, payload);
        } else {
            mViewModel.handleOnPinChanged(pin);
        }
    }

    /**
     * Add the adapter items to the list or update the existing items, deferring the request until
     * the current animation is finished or if no animation is running then the listener will be
     * automatically invoked immediately.
     *
     * @param items The array of {@link ScheduleItemHolder} to add.
     */
    private void addAdapterItems(final ScheduleItemHolder... items) {
        mLogger.d("addAdapterItems(items.size=%d).", items.length);

        if (mRecyclerView.isAnimating()) {
            // RecyclerView is currently animating -> defer update
            mRecyclerView.getItemAnimator().isRunning(() -> addAdapterItems(items));
        } else if (mRecyclerView.isComputingLayout()) {
            // RecyclerView is currently computing a layout -> defer update
            mRecyclerView.post(() -> addAdapterItems(items));
        } else {
            if (items.length == 1) {
                mItemAdapter.addItem(items[0]);
            } else if (items.length > 1) {
                mItemAdapter.addItems(items, /*mayModifyInput=*/ true);
            }

            // Now that we have schedule items, restore the selected schedule instance by its ID
            if (mSelectedScheduleId != INVALID_SCHEDULE_ID) {
                final ScheduleItemHolder itemHolder =
                    mItemAdapter.findItemById(mSelectedScheduleId);
                if (itemHolder != null) {
                    mSelectedSchedule = itemHolder.item;
                }
                mSelectedScheduleId = INVALID_SCHEDULE_ID;
            }

            // Expand the schedule before scrolling to it
            if (mScrollToScheduleId != INVALID_SCHEDULE_ID) {
                final ScheduleItemHolder itemHolder =
                    mItemAdapter.findItemById(mScrollToScheduleId);
                mRecyclerView.getItemAnimator().isRunning(() -> itemHolder.expand());
            }

            // Expand the correct schedule
            if (mExpandedScheduleId != INVALID_SCHEDULE_ID) {
                final ScheduleItemHolder itemHolder =
                    mItemAdapter.findItemById(mExpandedScheduleId);
                if (itemHolder != null) {
                    mScrollToScheduleId = mExpandedScheduleId;
                    if (mExpandedScheduleSavedState != null) {
                        itemHolder.onRestoreInstanceState(mExpandedScheduleSavedState);
                        mExpandedScheduleSavedState = null;
                    }
                } else {
                    mExpandedScheduleId = INVALID_SCHEDULE_ID;
                }
            }

            // Scroll to the correct schedule
            if (mScrollToScheduleId != INVALID_SCHEDULE_ID) {
                // Since our RecyclerView obtains items from a ConcatAdapter whose adapters are
                // populated asynchronously, for reliability, we'll start scrolling once everything
                // has been fully settled up
                mRecyclerView.post(() -> mRecyclerView.getItemAnimator().isRunning(() ->
                            scrollToSchedule(mScrollToScheduleId)));
            }
        }
    }

    @Override
    public void onClockClicked(final SubscriptionScheduleEntity schedule) {
        mLogger.d("onClockClicked(schedule=%s).", schedule);

        mSelectedSchedule = schedule;
        showTimePicker(schedule.getTime());
    }

    @Override
    public void onEditLabelClicked(final SubscriptionScheduleEntity schedule) {
        mLogger.d("onEditLabelClicked(schedule=%s).", schedule);

        mSelectedSchedule = schedule;

        final EditTextDialogFragment dialogFragment =
            new EditTextDialogFragment(EDIT_LABEL_RESULT_REQUEST_KEY);
        dialogFragment.setTitle(getString(R.string.scheduler_name));
        dialogFragment.setText(schedule.getLabel());
        dialogFragment.setSingleLineInput();
        dialogFragment.selectAllInputOnFocus();
        dialogFragment.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        dialogFragment.show(getParentFragmentManager(), EditTextDialogFragment.TAG);
    }

    @Override
    public void onDayOfWeekChanged(final SubscriptionScheduleEntity schedule,
            final @DayOfWeek int dayOfWeek, final boolean enabled) {

        mLogger.d("onDayOfWeekChanged(schedule=%s,dayOfWeek=%d,enabled=%s).", schedule, dayOfWeek,
                enabled);

        mSelectedSchedule = schedule;
        handleOnDayOfWeekChangedChanged(dayOfWeek, enabled);
    }

    @Override
    public void onEnabledStateChanged(final SubscriptionScheduleEntity schedule,
            final boolean enabled) {

        mLogger.d("onEnabledStateChanged(schedule=%s,enabled=%s).", schedule, enabled);

        mSelectedSchedule = schedule;
        handleOnEnabledStateChanged(enabled);
    }

    @Override
    public void onSubscriptionEnabledStateChanged(final SubscriptionScheduleEntity schedule,
            final boolean enabled) {

        mLogger.d("onSubscriptionEnabledStateChanged(schedule=%s,enabled=%s).", schedule, enabled);

        mSelectedSchedule = schedule;
        handleOnSubscriptionEnabledStateChanged(enabled);
    }

    @Override
    public void onDeleteClicked(final SubscriptionScheduleEntity schedule) {
        mLogger.d("onDeleteClicked(schedule=%s).", schedule);

        mSelectedSchedule = schedule;
        handleOnScheduleDeleted();
    }

    /**
     * @param action The action to run after authenticating the user.
     * @param payload The Bundle holding payload data.
     */
    private void authenticateAndRunAction(final String action, final Bundle payload) {
        final Intent i = new Intent(requireContext(), AuthenticationPromptActivity.class);
        i.setAction(action);
        if (payload != null) {
            i.putExtras(payload);
        }
        mAuthenticationPromptLauncher.launch(i);
    }

    /**
     * @param time The desired time to be used by the picker.
     */
    private void showTimePicker(final LocalTime time) {
        dismissTimePicker();
        final TimePickerDialogFragment dialogFragment =
            new TimePickerDialogFragment(TIME_PICKER_RESULT_REQUEST_KEY);
        dialogFragment.setHour(time.getHour());
        dialogFragment.setMinute(time.getMinute());
        dialogFragment.show(getParentFragmentManager(), TimePickerDialogFragment.TAG);
    }

    private void dismissTimePicker() {
        final TimePickerDialogFragment prevDialogFragment = (TimePickerDialogFragment)
            getParentFragmentManager().findFragmentByTag(TimePickerDialogFragment.TAG);
        if (prevDialogFragment != null) {
            getParentFragmentManager().beginTransaction().remove(prevDialogFragment).commit();
        }
    }

    /**
     * @param scheduleId The ID representing a schedule to be displayed.
     */
    private void scrollToSchedule(long scheduleId) {
        int localPosition = mItemAdapter.getPosition(scheduleId);
        if (localPosition != RecyclerView.NO_POSITION) {
            smoothScrollTo(getScheduleGlobalPosition(localPosition));
        }
    }

    private void smoothScrollTo(int position) {
        ((LayoutManager) mRecyclerView.getLayoutManager()).scrollToPositionWithOffset(position, 0);
    }

    /**
     * Get the global schedule position in the {@link #mConcatAdapter} using its local position
     * relative to the {@link #mItemAdapter}.
     */
    private int getScheduleGlobalPosition(final int localPosition) {
        return UiUtils.getGlobalPosition(mConcatAdapter, mItemAdapter, localPosition);
    }

    /**
     * @param scheduleId The schedule ID whose views should be rebind.
     */
    private void refreshScheduleItem(final long scheduleId) {
        final ScheduleItemHolder itemHolder = mItemAdapter.findItemById(scheduleId);
        if (itemHolder != null) {
            // Although, our item views don't rely on payloads, we pass some "dummy" data to avoid
            // triggering changing animations on rebind
            itemHolder.notifyItemChanged(scheduleId);
        }
    }

    @Override
    public RecyclerView onCreateRecyclerView(final LayoutInflater inflater, final ViewGroup parent,
            final Bundle savedInstanceState) {

        mRecyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState);
        mRecyclerView.setMotionEventSplittingEnabled(false);
        mRecyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(final RecyclerView rv, final MotionEvent e) {
                // Disable scrolling/user action to prevent choppy animations
                return rv.isAnimating();
            }
        });

        // Set our item animator to hook up item expansion/collapse animations callbacks
        mRecyclerView.setItemAnimator(new ItemAnimator());

        return mRecyclerView;
    }

    @Override
    public LayoutManager onCreateLayoutManager() {
        return new LayoutManager(requireContext());
    }

    @Override
    protected RecyclerView.Adapter<?> onCreateAdapter(final PreferenceScreen preferenceScreen) {
        final ConcatAdapter.Config adapterConfig = new ConcatAdapter.Config.Builder()
            .setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS)
            .build();
        // Wrap using ConcatAdapter so that we can later add the schedule item adapter
        return mConcatAdapter = new ConcatAdapter(adapterConfig,
                (RecyclerView.Adapter<?>) super.onCreateAdapter(preferenceScreen));
    }

    @Override
    public void onFragmentResult(final String requestKey, final Bundle bundle) {
        switch (requestKey) {
            case PIN_PROMPT_RESULT_REQUEST_KEY:
                handleOnPinChanged(bundle.getString(EditTextDialogFragment.EXTRA_TEXT));
                break;

            case EDIT_LABEL_RESULT_REQUEST_KEY:
                handleOnLabelChanged(bundle.getString(EditTextDialogFragment.EXTRA_TEXT));
                break;

            case TIME_PICKER_RESULT_REQUEST_KEY:
                handleOnTimePicked(bundle.getString(TimePickerDialogFragment.EXTRA_TIME));
                break;

            default:
                mLogger.wtf(new RuntimeException("Unhandled fragment result: " + requestKey +
                            ",bundle = " + bundle));
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(SAVED_PIN_POPUP_VISIBLE, mPinPopupMenuVisible);
        if (mExpandedScheduleId != INVALID_SCHEDULE_ID) {
            outState.putLong(SAVED_EXPANDED_SCHEDULE_ID, mExpandedScheduleId);
            final ScheduleItemHolder itemHolder = mItemAdapter.findItemById(mExpandedScheduleId);
            if (itemHolder != null) {
                final Bundle bundle = new Bundle();
                itemHolder.onSaveInstanceState(bundle);
                outState.putBundle(SAVED_EXPANDED_SCHEDULE_STATE, bundle);
            }
        }
        if (mSelectedSchedule != null) {
            outState.putLong(SAVED_SELECTED_SCHEDULE_ID, mSelectedSchedule.getId());
        }
    }

    @Override
    public void onViewStateRestored(final Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null) {
            if ((mPinPopupMenuVisible = savedInstanceState.getBoolean(SAVED_PIN_POPUP_VISIBLE))) {
                mPinFab.post(() -> mPinPopupMenu.show());
            }
            mExpandedScheduleId = savedInstanceState.getLong(SAVED_EXPANDED_SCHEDULE_ID,
                    INVALID_SCHEDULE_ID);
            mExpandedScheduleSavedState =
                savedInstanceState.getBundle(SAVED_EXPANDED_SCHEDULE_STATE);
            mSelectedScheduleId = savedInstanceState.getLong(SAVED_SELECTED_SCHEDULE_ID,
                    INVALID_SCHEDULE_ID);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mPinPopupMenu != null) {
            mPinPopupMenu.dismiss();
        }
    }

    /**
     * This class will be used by the {@link RecyclerView} to accomodate more items in the viewport
     * by leveraging the {@link AppBarLayout} expansion. It also aims to handle the RV preemptive
     * height shrinkage before schedule item collapse animation is finished, and some other tweaks
     * to improve UX.
     */
    private class LayoutManager extends LinearLayoutManager {
        boolean mAutoMeasureEnabled = true;
        int mVerticalScrollOffset;
        int mHeightSize;
        boolean mAppBarLayoutInitialized;

        final AppBarLayout mAppBarLayout;

        public LayoutManager(final Context context) {
            super(context);

            mAppBarLayout = ((SchedulerActivity) getActivity()).getAppBarLayout();
        }

        @Override
        public void onLayoutChildren(final RecyclerView.Recycler recycler,
                final RecyclerView.State state) {

            super.onLayoutChildren(recycler, state);

            // Compute vertical scroll offset when the "real" layout is done, since during
            // predictive animations the onLayoutChildren will be called twice; once as a "pre"
            // layout step to determine where items would have been prior to a real layout, and
            // again to do the "real" layout
            if (!state.isPreLayout()) {
                mVerticalScrollOffset = mRecyclerView.computeVerticalScrollOffset();
            }
        }

        @Override
        public void onLayoutCompleted(final RecyclerView.State state) {
            super.onLayoutCompleted(state);

            // Adjust the AppBarLayout expansion depending on the current screen orientation for a
            // better view of the expanded schedule if there's one otherwise try to accomodate as
            // much items as possible
            if (mAppBarLayout != null) {
                final boolean expanded;
                if (UiUtils.isLandscape(getContext())) {
                    int itemCount = mConcatAdapter.getItemCount();
                    if (mEmptyViewPref.isVisible()) {
                        // Exclude the empty view from the total item count if visible, since it's
                        // part of the RecyclerView and will be effectively visible if and only if
                        // there's no schedule items
                        itemCount--;
                    }
                    // Always expand AppBarLayout in landscape when RecyclerView contains no
                    // schedule or other items
                    expanded = itemCount == 0;
                } else {
                    if (mVerticalScrollOffset > 0) {
                        // Always collapse AppBarLayout when the item list has been scrolled down,
                        // so that we can accomodate more items in the viewport
                        expanded = false;
                    } else {
                        // Always expand AppBarLayout if there's no expanded schedule since we're
                        // already at the beginning of the list, or if the RecyclerView's bottom
                        // edge doesn't overlap with FAB buttons
                        expanded = mExpandedScheduleId == INVALID_SCHEDULE_ID ||
                            mRecyclerView.getBottom() <= mAddFab.getLocationOnScreen()[1];
                    }
                }
                mAppBarLayout.setExpanded(expanded, /*animate=*/ mAppBarLayoutInitialized);
                mAppBarLayoutInitialized = true;
            }
        }

        @Override
        protected void calculateExtraLayoutSpace(final RecyclerView.State state,
                final int[] extraLayoutSpace) {
            // We need enough space so after expand/collapse, other items are still shown
            // properly. The multiplier was chosen after tests
            extraLayoutSpace[0] = 2 * getHeight();
            extraLayoutSpace[1] = extraLayoutSpace[0];
        }

        @Override
        public void setMeasuredDimension(final int widthSize, final int heightSize) {
            // If during auto-measurements, the previously saved RecyclerView's height results to be
            // bigger, then we expect the RV to shrink in height. Unfortunately, when RV shrinks due
            // to item collapse/removal with auto-measurements enabled, we get clipped items when
            // running animations. To workaround this, we'll force the previous RV height and
            // re-measure after the current animation is finished
            if (mHeightSize > heightSize) {
                mAutoMeasureEnabled = false;
                mRecyclerView.post(() -> mRecyclerView.getItemAnimator().isRunning(() -> {
                    mAutoMeasureEnabled = true;
                    requestLayout();
                }));
                super.setMeasuredDimension(widthSize, mHeightSize);
            } else {
                super.setMeasuredDimension(widthSize, heightSize);
            }
            mHeightSize = heightSize;
        }

        @Override
        public void onMeasure(final RecyclerView.Recycler recycler, final RecyclerView.State state,
                final int widthSpec, final int heightSpec) {

            final Resources res = getResources();

            // Add the spacing below to avoid the last schedule item being covered by FAB buttons
            if (!UiUtils.isLandscape(getContext())) {
                mRecyclerView.setPadding(0, 0, 0, res.getDimensionPixelSize(R.dimen.fab_height));
                mRecyclerView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
            } else {
                // In landscape we want all the vertical space and FABs on one side
                mRecyclerView.setPaddingRelative(0, 0,
                        res.getDimensionPixelSize(R.dimen.fab_container_land_width),
                        res.getDimensionPixelSize(R.dimen.schedule_margin_top));
            }

            super.onMeasure(recycler, state, widthSpec, heightSpec);

            // A call to onMeasure method while there are 0 items most likely means that the last
            // item has been removed and the RecyclerView height shrinkage is expected. We want to
            // force the old RV height and postpone the auto-measurement until the current animation
            // is finished using setMeasuredDimension method. But in this particular case, there
            // will be no call to LayoutManager.setMeasuredDimension method, so we should make this
            // call manually with the previous height size
            if (getItemCount() == 0) {
                setMeasuredDimension(View.MeasureSpec.getSize(widthSpec), mHeightSize);
            }
        }

        @Override
        public boolean isAutoMeasureEnabled() {
            // Besides the flag, consider also if we have at least one item in the list. This is
            // needed so that we can set our desired RV height using setMeasuredDimension method
            // within onMeasure
            return getItemCount() > 0 && mAutoMeasureEnabled;
        }

        @Override
        public void onItemsUpdated(RecyclerView recyclerView, int positionStart, int itemCount) {
            super.onItemsUpdated(recyclerView, positionStart, itemCount);

            // Run simple animations in the next layout pass as the items could have collapsed,
            // which will subsequently lead to RecyclerView shrinkage
            requestSimpleAnimationsInNextLayout();
        }

        @Override
        public void onItemsRemoved(RecyclerView recyclerView, int positionStart, int itemCount) {
            super.onItemsRemoved(recyclerView, positionStart, itemCount);

            // Run simple animations in the next layout pass as the items have been removed, which
            // will subsequently lead to RecyclerView shrinkage
            requestSimpleAnimationsInNextLayout();
        }
    }
}
