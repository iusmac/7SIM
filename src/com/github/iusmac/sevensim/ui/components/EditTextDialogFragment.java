package com.github.iusmac.sevensim.ui.components;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentResultListener;

import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.Utils;

/**
 * A {@link DialogFragment} prompting the user for input.
 */
public class EditTextDialogFragment extends DialogFragment {
    public static final String TAG = EditTextDialogFragment.class.getSimpleName();

    private static final String SAVED_REQUEST_KEY = "requestKey";
    private static final String SAVED_TITLE = "title";
    private static final String SAVED_INPUT_TYPE = "inputType";
    private static final String SAVED_MAX_LENGTH = "maxLength";
    private static final String SAVED_SINGLE_LINE_INPUT = "singleLineInput";

    /** Handle completing the {@link EditText} from the IME keyboard. */
    private final TextView.OnEditorActionListener mImeDoneListener = (v, actionId, event) -> {
        // Check if this was the result of hitting the enter key
        if (event == null && (actionId == EditorInfo.IME_NULL
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_NEXT)) {
            onDone();
            dismissAllowingStateLoss();
            return true;
        }
        return false;
    };

    /**
     * The default request key string passed in
     * {@link FragmentResultListener#onFragmentResult(String,Bundle)} to identify the result when
     * the user is done filling in the input field and pressed the "OK" button.
     */
    public static final String DEFAULT_REQUEST_KEY = "requestKey";

    /** Key holding the result passed in
     * {@link FragmentResultListener#onFragmentResult(String,Bundle)}. */
    public static final String EXTRA_TEXT = "text";

    private String mRequestKey;
    private EditText mEditText;
    private String mTitle;
    private String mText;
    private int mInputType = -1;
    private int mMaxLength = -1;
    private boolean mSingleLineInput;
    private boolean mSelectAllInputOnFocus;

    public EditTextDialogFragment() {
    }

    /**
     * @param requestKey The request key string for
     * {@link FragmentResultListener#onFragmentResult(String,Bundle)} to identify the result when
     * the user is done filling in the input field and pressed the "OK" button, or {@code null} to
     * use {@link #DEFAULT_REQUEST_KEY}.
     */
    public EditTextDialogFragment(final @Nullable String requestKey) {
        mRequestKey = requestKey;
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final View editTextContainer = getLayoutInflater().inflate(R.layout.dialog_edittext, null);
        mEditText = editTextContainer.findViewById(android.R.id.edit);

        if (savedInstanceState != null) {
            mRequestKey = savedInstanceState.getString(SAVED_REQUEST_KEY);
            setTitle(savedInstanceState.getString(SAVED_TITLE));
            setInputType(savedInstanceState.getInt(SAVED_INPUT_TYPE));
            setMaxInputLength(savedInstanceState.getInt(SAVED_MAX_LENGTH));
            setSingleLineInput(savedInstanceState.getBoolean(SAVED_SINGLE_LINE_INPUT));
        }

        final AlertDialog alertDialog = new AlertDialog.Builder(requireContext())
            .setTitle(mTitle == null ? getString(R.string.app_name) : mTitle)
            .setView(editTextContainer)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> onDone())
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        mEditText.setOnEditorActionListener(mImeDoneListener);

        // Trigger soft keyboard opening
        final Window window = alertDialog.getWindow();
        if (Utils.IS_AT_LEAST_R) {
            window.getDecorView().getWindowInsetsController().show(WindowInsets.Type.ime());
        } else {
            mEditText.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    window.setSoftInputMode(WindowManager.LayoutParams
                            .SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }
            });
        }
        mEditText.requestFocus();
        if (mInputType != -1) {
            mEditText.setInputType(mInputType);
        }
        if (mMaxLength > -1) {
            mEditText.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(mMaxLength)
            });
        }

        if (mText != null) {
            mEditText.setText(mText);
        }
        if (mSingleLineInput != false) {
            mEditText.setSingleLine(mSingleLineInput);
        }
        if (mSelectAllInputOnFocus) {
            mEditText.selectAll();
        }

        return alertDialog;
    }

    /**
     * Set the title of the dialog.
     *
     * @param title The title string.
     */
    public void setTitle(final @Nullable String title) {
        mTitle = title;
    }

    /**
     * Set the text for the {@link EditText}.
     *
     * @param text The initial text string to display in the {@link EditText}.
     *
     * @attr ref android.R.styleable#TextView_text
     */
    public void setText(final @Nullable String text) {
        mText = text;
    }

    /**
     * Set the type of the content using {@link InputType} constants.
     *
     * @attr ref android.R.styleable#TextView_inputType
     *
     * @param type The {@link InputType} flags.
     */
    public void setInputType(final int type) {
        mInputType = type;
    }

    /**
     * Restrict the input length to an arbitrary amount of characters.
     *
     * @attr ref android.R.styleable#TextView_maxLength
     *
     * @param maxLength The maximum number of characters.
     */
    public void setMaxInputLength(final int maxLength) {
        mMaxLength = maxLength;
    }

    /**
     * Convenience method for calling {@link #setSingleLineInput(boolean)} with {@code true}.
     */
    public void setSingleLineInput() {
        setSingleLineInput(true);
    }

    /**
     * Restrict the {@link EditText} to a single-line input.
     *
     * @attr ref android.R.styleable#TextView_singleLine
     *
     * @param singleLineInput Whether to constraint the {@link EditText} to a single-line input.
     */
    public void setSingleLineInput(final boolean singleLineInput) {
        mSingleLineInput = singleLineInput;
    }

    /**
     * Convenience method for calling {@link #selectAllInputOnFocus(boolean)} with {@code true}.
     */
    public void selectAllInputOnFocus() {
        selectAllInputOnFocus(true);
    }

    /**
     * Select all the text on dialog open.
     *
     * @attr ref android.R.styleable#TextView_selectAllOnFocus
     *
     * @param selectAllInputOnFocus Whether to select all text in {@link EditText} on dialog open.
     */
    public void selectAllInputOnFocus(final boolean selectAllInputOnFocus) {
        mSelectAllInputOnFocus = selectAllInputOnFocus;
    }

    private void onDone() {
        final String requestKey = mRequestKey == null ? DEFAULT_REQUEST_KEY : mRequestKey;
        final Bundle result = new Bundle(1);
        result.putString(EXTRA_TEXT, mEditText.getText().toString());
        getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(SAVED_REQUEST_KEY, mRequestKey);
        outState.putString(SAVED_TITLE, mTitle);
        outState.putInt(SAVED_INPUT_TYPE, mInputType);
        outState.putInt(SAVED_MAX_LENGTH, mMaxLength);
        outState.putBoolean(SAVED_SINGLE_LINE_INPUT, mSingleLineInput);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Stop callbacks from the IME since there's no view to process them
        mEditText.setOnEditorActionListener(null);
    }
}
