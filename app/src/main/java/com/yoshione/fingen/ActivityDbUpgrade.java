package com.yoshione.fingen;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.yoshione.fingen.databinding.ActivityDbUpgradeBinding;
import com.yoshione.fingen.db.DbUpgradeException;
import com.yoshione.fingen.db.DbUpgradeGate;
import com.yoshione.fingen.utils.DebouncingOnClickListener;
import com.yoshione.fingen.utils.WindowInsetsHelper;

/**
 * Shows the progress of the DB upgrade (migration runs in DbUpgradeGate background thread) and its errors.
 * Screens that need the DB call {@link #redirectIfNeeded(Activity)} first; on success this screen
 * starts the screen that was replaced, with the same intent.
 */
public class ActivityDbUpgrade extends AppCompatActivity implements DbUpgradeGate.Listener {

    private static final String EXTRA_TARGET = "db_upgrade_target";

    private ActivityDbUpgradeBinding mBinding;
    private boolean mProceeded = false;
    private boolean mWaitingFreeSpace = false;

    /**
     * If the DB needs an upgrade, starts this screen instead of the given one and finishes it.
     *
     * @return true if redirected; the caller must stop initialization (no DB access)
     */
    public static boolean redirectIfNeeded(Activity from) {
        if (!DbUpgradeGate.needsUpgrade(from)) {
            return false;
        }
        Intent src = from.getIntent();
        Intent intent = new Intent(from, ActivityDbUpgrade.class);
        intent.setAction(src.getAction());
        intent.setData(src.getData());
        if (src.getExtras() != null) {
            intent.putExtras(src.getExtras());
        }
        intent.putExtra(EXTRA_TARGET, from.getClass().getName());
        from.startActivity(intent);
        from.finish();
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!DbUpgradeGate.needsUpgrade(this)) {
            proceed();
            return;
        }
        WindowInsetsHelper.enable(this);
        mBinding = ActivityDbUpgradeBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        WindowInsetsHelper.applyToRoot(mBinding.getRoot(), null);

        mBinding.dbUpgradeRetry.setOnClickListener(new DebouncingOnClickListener(v -> DbUpgradeGate.start(this)));
        mBinding.dbUpgradeFreeSpace.setOnClickListener(new DebouncingOnClickListener(v -> {
            mWaitingFreeSpace = true;
            try {
                startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
            } catch (RuntimeException e) {
                // No storage settings screen: check again right away
                mWaitingFreeSpace = false;
                DbUpgradeGate.start(this);
            }
        }));

        // The upgrade must not be interrupted: "back" does nothing while it runs, afterwards it only hides the app
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (DbUpgradeGate.getState() == DbUpgradeGate.FAILED) {
                    moveTaskToBack(true);
                }
            }
        });

        if (DbUpgradeGate.getState() == DbUpgradeGate.IDLE) {
            DbUpgradeGate.start(this);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mBinding != null) {
            DbUpgradeGate.setListener(this);
            render();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWaitingFreeSpace) {
            // Back from the storage settings: check the space again
            mWaitingFreeSpace = false;
            DbUpgradeGate.start(this);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        DbUpgradeGate.setListener(null);
    }

    @Override
    public void onChanged() {
        if (!isFinishing() && !isDestroyed()) {
            render();
        }
    }

    private void render() {
        switch (DbUpgradeGate.getState()) {
            case DbUpgradeGate.DONE:
                proceed();
                break;
            case DbUpgradeGate.FAILED:
                renderError();
                break;
            default:
                mBinding.dbUpgradeProgress.setVisibility(View.VISIBLE);
                mBinding.dbUpgradeProgress.setProgress(DbUpgradeGate.getPercent());
                mBinding.dbUpgradeStage.setVisibility(View.VISIBLE);
                mBinding.dbUpgradeStage.setText(getStageText(DbUpgradeGate.getStage()));
                mBinding.dbUpgradeMessage.setText(R.string.db_upgrade_hint);
                mBinding.dbUpgradeRetry.setVisibility(View.GONE);
                mBinding.dbUpgradeFreeSpace.setVisibility(View.GONE);
                break;
        }
    }

    private int getStageText(int stage) {
        switch (stage) {
            case DbUpgradeGate.STAGE_CONVERT:
                return R.string.db_upgrade_stage_convert;
            case DbUpgradeGate.STAGE_BALANCE:
                return R.string.db_upgrade_stage_balance;
            case DbUpgradeGate.STAGE_VERIFY:
                return R.string.db_upgrade_stage_verify;
            default:
                return R.string.db_upgrade_stage_prepare;
        }
    }

    private void renderError() {
        mBinding.dbUpgradeProgress.setVisibility(View.GONE);
        mBinding.dbUpgradeStage.setVisibility(View.GONE);
        mBinding.dbUpgradeRetry.setVisibility(View.VISIBLE);
        int code = DbUpgradeGate.getErrorCode();
        if (code == DbUpgradeException.CODE_NO_SPACE) {
            long mb = (DbUpgradeGate.getRequiredBytes() + 1024 * 1024 - 1) / (1024 * 1024);
            mBinding.dbUpgradeMessage.setText(getString(R.string.db_upgrade_no_space, mb));
            mBinding.dbUpgradeFreeSpace.setVisibility(View.VISIBLE);
        } else {
            mBinding.dbUpgradeMessage.setText(code == DbUpgradeException.CODE_VERIFY
                    ? R.string.db_upgrade_verify_failed : R.string.db_upgrade_failed);
            mBinding.dbUpgradeFreeSpace.setVisibility(View.GONE);
        }
    }

    /** Opens the screen that was replaced (or the main screen) and closes this one. */
    private void proceed() {
        if (mProceeded) {
            return;
        }
        mProceeded = true;
        Intent next = new Intent(getIntent());
        String target = next.getStringExtra(EXTRA_TARGET);
        next.removeExtra(EXTRA_TARGET);
        next.setComponent(new ComponentName(this, target != null ? target : ActivityMain.class.getName()));
        startActivity(next);
        finish();
    }
}
