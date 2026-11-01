package com.yoshione.fingen.adapter.viewholders;

import android.content.Context;
import androidx.core.content.ContextCompat;
import android.view.View;
import android.widget.TextView;

import com.yoshione.fingen.R;
import com.yoshione.fingen.databinding.ListItemProductRefBinding;
import com.yoshione.fingen.model.Product;

import java.util.List;

/**
 * Created by slv on 25.12.2015.
 *
 */
public class VhProduct extends VhModelBase {
    TextView textViewName;
    Context mContext;

    public VhProduct(View itemView, IOnItemClickListener onItemClickListener, Context context) {
        super(itemView, onItemClickListener, context);
        ListItemProductRefBinding binding = ListItemProductRefBinding.bind(itemView);
        textViewName = binding.textViewName;
        mContext = context;
    }

    public void bindViewHolder(List<?> mModelList, int position) {
        super.bindViewHolder(mModelList, position);
        Product product = (Product) mModelList.get(position);
        textViewName.setText(product.getName());
    }
}
