/*
 * Copyright (c) 2015.
 */

package com.yoshione.fingen;

import com.yoshione.fingen.utils.DebouncingOnClickListener;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.yoshione.fingen.dao.CabbagesDAO;
import com.yoshione.fingen.databinding.FragmentCabbageEditBinding;
import com.yoshione.fingen.managers.CabbageManager;
import com.yoshione.fingen.managers.CurrencyScale;
import com.yoshione.fingen.model.Cabbage;

import java.util.Currency;

/**
 * Created by slv on 04.12.2015.
 *
 */
public class FragmentCabbageEdit extends DialogFragment {

    Button buttonSystemCurrencies;
    EditText editTextName;
    EditText editTextCode;
    EditText editTextSymbol;
    EditText editTextScale;
    private FragmentCabbageEditBinding mBinding;

    public FragmentCabbageEdit() {
    }

    public static FragmentCabbageEdit newInstance(String title, Cabbage cabbage) {
        FragmentCabbageEdit frag = new FragmentCabbageEdit();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putParcelable("cabbage", cabbage);
        frag.setArguments(args);

        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String title = getArguments().getString("title");
        Cabbage cabbage = getArguments().getParcelable("cabbage");
        View view = getActivity().getLayoutInflater().inflate(R.layout.fragment_cabbage_edit, null);
        mBinding = FragmentCabbageEditBinding.bind(view);
        buttonSystemCurrencies = mBinding.buttonSystemCurrencies;
        editTextName = mBinding.editTextName;
        editTextCode = mBinding.editTextCode;
        editTextSymbol = mBinding.editTextSymbol;
        editTextScale = mBinding.editTextScale;
        mBinding.buttonSystemCurrencies.setOnClickListener(new DebouncingOnClickListener(v -> selectSystemCurrencyClick()));

        if (cabbage != null) {
            editTextName.setText(cabbage.getName());
            editTextCode.setText(cabbage.getCode());
            editTextSymbol.setText(cabbage.getSimbol());
            editTextScale.setText(String.valueOf(cabbage.getDecimalCount()));
        }

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
        alertDialogBuilder.setView(view);
        alertDialogBuilder.setTitle(title);
        alertDialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        alertDialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });


        return alertDialogBuilder.create();
    }

    private boolean isInputValid() {
        return !editTextName.getText().toString().isEmpty() &
                !editTextCode.getText().toString().isEmpty() &
                !editTextSymbol.getText().toString().isEmpty();
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog d = (AlertDialog) getDialog();
        if (d != null) {
            Button positiveButton = d.getButton(Dialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(new OnOkListener());
        }
    }

    private class OnOkListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            if (isInputValid()) {
                Cabbage cabbage = getArguments().getParcelable("cabbage");
                if (cabbage != null) {
                    cabbage.setName(editTextName.getText().toString());
                    cabbage.setCode(editTextCode.getText().toString());
                    cabbage.setSimbol(editTextSymbol.getText().toString());
                    // Знаки после запятой: 0..8, пустое поле - 2 (суммы хранятся целыми в минорных единицах)
                    int decimalCount = 2;
                    String scaleText = editTextScale.getText().toString().trim();
                    if (!scaleText.isEmpty()) {
                        try {
                            decimalCount = Integer.parseInt(scaleText);
                        } catch (NumberFormatException e) {
                            decimalCount = -1;
                        }
                        if (decimalCount < 0 || decimalCount > CurrencyScale.MAX_DIGITS) {
                            editTextScale.setError(getString(R.string.err_currency_scale_range));
                            Toast.makeText(getActivity(), R.string.err_currency_scale_range, Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    cabbage.setDecimalCount(decimalCount);
                    CabbagesDAO cabbagesDAO = CabbagesDAO.getInstance(getActivity());
                    try {
                        cabbagesDAO.createModel(cabbage);
                    } catch (Exception e) {
                        Toast.makeText(getActivity(), R.string.msg_error_on_write_to_db, Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                dismiss();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }

    void selectSystemCurrencyClick() {
        CabbageManager.getInstance().showSelectSystemCurrencyDialog(getActivity(), new CabbageManager.OnSelectCurrencyListener() {
            @Override
            public void OnSelectCurrency(Currency selectedCurrency) {
                editTextName.setText(selectedCurrency.getDisplayName());
                editTextCode.setText(selectedCurrency.getCurrencyCode());
                editTextSymbol.setText(selectedCurrency.getSymbol());
            }
        });
    }
}