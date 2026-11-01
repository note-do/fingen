package com.yoshione.fingen.lock;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import com.yoshione.fingen.utils.WindowInsetsHelper;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.yoshione.fingen.R;

/**
 * Enters, creates or confirms the PIN. Used both as lock screen and as settings dialog.
 */
public class PinActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "pin_mode";
    public static final int MODE_UNLOCK = 0;
    public static final int MODE_ENABLE = 1;
    public static final int MODE_DISABLE = 2;
    public static final int MODE_CHANGE = 3;

    private static final int STEP_VERIFY = 0;
    private static final int STEP_CREATE = 1;
    private static final int STEP_CONFIRM = 2;

    private TextView mStepTextView;
    private TextView mDotsTextView;
    private TextView mErrorTextView;

    private int mMode;
    private int mStep;
    private int mPinLength;
    private String mInput = "";
    private String mNewPin;

    public static Intent unlockIntent(Context context) {
        Intent intent = new Intent(context, PinActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_UNLOCK);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowInsetsHelper.enable(this);
        setContentView(R.layout.activity_pin_code);
        WindowInsetsHelper.applyToRoot(((ViewGroup) findViewById(android.R.id.content)).getChildAt(0), null);

        mStepTextView = findViewById(R.id.pin_code_step_textview);
        mDotsTextView = findViewById(R.id.pin_code_dots_textview);
        mErrorTextView = findViewById(R.id.pin_code_error_textview);

        mMode = getIntent().getIntExtra(EXTRA_MODE, MODE_UNLOCK);
        mPinLength = PinLockManager.getPinLength(this);
        goToStep(mMode == MODE_ENABLE ? STEP_CREATE : STEP_VERIFY);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mMode == MODE_UNLOCK) {
                    // Keep the app locked and leave it in background.
                    moveTaskToBack(true);
                } else {
                    setResult(RESULT_CANCELED);
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    public void onKeyClick(View view) {
        mErrorTextView.setVisibility(View.INVISIBLE);
        String key = (String) view.getTag();
        if ("del".equals(key)) {
            if (mInput.length() > 0) {
                mInput = mInput.substring(0, mInput.length() - 1);
            }
        } else if (mInput.length() < mPinLength) {
            mInput += key;
        }
        updateDots();
        if (mInput.length() == mPinLength) {
            onPinEntered();
        }
    }

    @Override
    protected void onDestroy() {
        if (mMode == MODE_UNLOCK) {
            PinLockManager.onPinDismissed();
        }
        super.onDestroy();
    }

    private void onPinEntered() {
        switch (mStep) {
            case STEP_VERIFY:
                if (!PinLockManager.checkPin(this, mInput)) {
                    showError(R.string.pin_code_wrong);
                    goToStep(STEP_VERIFY);
                } else if (mMode == MODE_UNLOCK) {
                    PinLockManager.onUnlocked();
                    finishWithResult(RESULT_OK);
                } else if (mMode == MODE_DISABLE) {
                    PinLockManager.clearPin(this);
                    finishWithResult(RESULT_OK);
                } else {
                    goToStep(STEP_CREATE);
                }
                break;
            case STEP_CREATE:
                mNewPin = mInput;
                goToStep(STEP_CONFIRM);
                break;
            case STEP_CONFIRM:
                if (mInput.equals(mNewPin)) {
                    PinLockManager.setPin(this, mInput);
                    finishWithResult(RESULT_OK);
                } else {
                    showError(R.string.pin_code_mismatch);
                    goToStep(STEP_CREATE);
                }
                break;
        }
    }

    private void finishWithResult(int resultCode) {
        setResult(resultCode);
        finish();
    }

    private void goToStep(int step) {
        mStep = step;
        mInput = "";
        updateStepText();
        updateDots();
    }

    private void updateStepText() {
        int textRes;
        switch (mStep) {
            case STEP_CREATE:
                mStepTextView.setText(String.format(getString(R.string.pin_code_step_create), mPinLength));
                return;
            case STEP_CONFIRM:
                textRes = R.string.pin_code_step_enable_confirm;
                break;
            default:
                if (mMode == MODE_UNLOCK) {
                    textRes = R.string.pin_code_step_unlock;
                } else if (mMode == MODE_DISABLE) {
                    textRes = R.string.pin_code_step_disable;
                } else {
                    textRes = R.string.pin_code_step_change;
                }
        }
        mStepTextView.setText(textRes);
    }

    private void updateDots() {
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < mPinLength; i++) {
            dots.append(i < mInput.length() ? "●" : "○");
            if (i < mPinLength - 1) {
                dots.append(' ');
            }
        }
        mDotsTextView.setText(dots);
    }

    private void showError(int textRes) {
        mErrorTextView.setText(textRes);
        mErrorTextView.setVisibility(View.VISIBLE);
    }
}
