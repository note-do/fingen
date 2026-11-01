package com.yoshione.fingen.di;

import android.content.Context;

import com.yoshione.fingen.ActivityEditTransaction;
import com.yoshione.fingen.ActivityMain;
import com.yoshione.fingen.FragmentAccounts;
import com.yoshione.fingen.FragmentSummary;
import com.yoshione.fingen.FragmentTransactions;
import com.yoshione.fingen.adapter.viewholders.TransactionViewHolderParams;
import com.yoshione.fingen.di.modules.ContextModule;
import com.yoshione.fingen.di.modules.DaoModule;
import com.yoshione.fingen.di.modules.PreferencesModule;
import com.yoshione.fingen.widgets.ToolbarActivity;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {ContextModule.class, PreferencesModule.class, DaoModule.class})
public interface AppComponent {
	Context getContext();

	void inject(ActivityMain activityMain);
	void inject(FragmentAccounts fragmentAccounts);
	void inject(FragmentSummary fragmentSummary);
	void inject(FragmentTransactions fragmentTransactions);
	void inject(ToolbarActivity toolbarActivity);
	void inject(ActivityEditTransaction activityEditTransaction);
	void inject(TransactionViewHolderParams transactionViewHolderParams);
}
