package com.yoshione.fingen.adapter.viewholders;

import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.yoshione.fingen.R;
import com.yoshione.fingen.databinding.ListItemUserPermissionBinding;

/**
 * Created by slv on 14.03.2017.
 * UserPermissionViewHolder
 */

public class UserPermissionViewHolder extends RecyclerView.ViewHolder {
    ImageView imageViewIcon;
    TextView mTextViewEmail;
    CheckBox mCheckBoxRead;
    CheckBox mCheckBoxWrite;

    public UserPermissionViewHolder(View itemView) {
        super(itemView);
        itemView.setLongClickable(true);
        ListItemUserPermissionBinding binding = ListItemUserPermissionBinding.bind(itemView);
        imageViewIcon = binding.imageViewIcon;
        mTextViewEmail = binding.textViewEmail;
        mCheckBoxRead = binding.checkBoxRead;
        mCheckBoxWrite = binding.checkBoxWrite;
    }

    public void setEmail(String email) {
        mTextViewEmail.setText(email);
    }

    public void setRead(boolean read) {
        mCheckBoxRead.setChecked(read);
    }

    public void setWrite(boolean write) {
        mCheckBoxWrite.setChecked(write);
    }

    public void setOnReadChangeListener(CompoundButton.OnCheckedChangeListener listener) {
        mCheckBoxRead.setOnCheckedChangeListener(listener);
    }

    public void setOnWriteChangeListener(CompoundButton.OnCheckedChangeListener listener) {
        mCheckBoxWrite.setOnCheckedChangeListener(listener);
    }
}
