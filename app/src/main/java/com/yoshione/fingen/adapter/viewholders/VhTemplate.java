package com.yoshione.fingen.adapter.viewholders;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.yoshione.fingen.dao.AccountsDAO;
import com.yoshione.fingen.databinding.ListItemTemplatesBinding;
import com.yoshione.fingen.managers.AccountManager;
import com.yoshione.fingen.model.Account;
import com.yoshione.fingen.model.Template;
import com.yoshione.fingen.model.Transaction;
import com.yoshione.fingen.R;
import com.yoshione.fingen.utils.AmountColorizer;
import com.yoshione.fingen.utils.CabbageFormatter;
import com.yoshione.fingen.utils.IconGenerator;

import java.util.List;

/**
 * Created by slv on 06.04.2016.
 *
 */
public class VhTemplate extends VhModelBase {


    TextView textViewAmount;
    ImageView imageViewIcon;
    FrameLayout mSpaceBottom;
    FrameLayout mSpaceBottomFinal;


    TextView textViewName;
    //    @BindView(R.id.textViewAmount)
//    TextView textViewAmount;
    private final AmountColorizer mAmountColorizer;

    public VhTemplate(View itemView, IOnItemClickListener onItemClickListener, Context mContext) {
        super(itemView, onItemClickListener, mContext);
        ListItemTemplatesBinding binding = ListItemTemplatesBinding.bind(itemView);
        textViewAmount = binding.textViewAmount;
        imageViewIcon = binding.imageviewIcon;
        mSpaceBottom = binding.spaceBottom;
        mSpaceBottomFinal = binding.spaceBottomFinal;
        textViewName = binding.textViewName;
        mAmountColorizer = new AmountColorizer(mContext);
    }

    @Override
    public void bindViewHolder(List<?> mModelList, int position) {
        super.bindViewHolder(mModelList, position);
        Template template = (Template) mModelList.get(position);

        textViewName.setText(template.getName());

        AccountsDAO accountsDAO = AccountsDAO.getInstance(mContext);
        Account srcAccount = accountsDAO.getAccountByID(template.getAccountID());
        CabbageFormatter cabbageFormatter = new CabbageFormatter(AccountManager.getCabbage(srcAccount, mContext));
        textViewAmount.setText(cabbageFormatter.format(template.getAmount()));

        textViewAmount.setTextColor(mAmountColorizer.getTransactionColor(template.getTrType()));
        imageViewIcon.setImageDrawable(mAmountColorizer.getTransactionIcon(template.getTrType()));

        if (position == mModelList.size() - 1) {
            mSpaceBottom.setVisibility(View.GONE);
            mSpaceBottomFinal.setVisibility(View.VISIBLE);
        } else {
            mSpaceBottom.setVisibility(View.VISIBLE);
            mSpaceBottomFinal.setVisibility(View.GONE);
        }
    }
}
