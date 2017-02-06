package cm.aptoide.pt.v8engine.fragment;

import android.accounts.AccountManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import cm.aptoide.accountmanager.AptoideAccountManager;
import cm.aptoide.pt.dataprovider.DataProvider;
import cm.aptoide.pt.dataprovider.repository.IdsRepositoryImpl;
import cm.aptoide.pt.preferences.Application;
import cm.aptoide.pt.preferences.secure.SecureCoderDecoder;
import cm.aptoide.pt.preferences.secure.SecurePreferencesImplementation;
import cm.aptoide.pt.v8engine.repository.request.RequestFactory;
import cm.aptoide.pt.v8engine.util.StoreCredentialsProviderImpl;
import cm.aptoide.pt.v8engine.view.recycler.base.BaseAdapter;

/**
 * Created by neuro on 03-01-2017.
 */

public class AptoideBaseFragment<T extends BaseAdapter> extends GridRecyclerFragment<T> {

  protected RequestFactory requestFactory;

  @Override public void onCreate(@Nullable Bundle savedInstanceState) {
    requestFactory = new RequestFactory(new IdsRepositoryImpl(SecurePreferencesImplementation.getInstance(),
        DataProvider.getContext()), AptoideAccountManager.getInstance(getContext(),
        Application.getConfiguration(), new SecureCoderDecoder.Builder(getContext().getApplicationContext()).create(),
        AccountManager.get(getContext().getApplicationContext()), new IdsRepositoryImpl(SecurePreferencesImplementation.getInstance(),
            getContext().getApplicationContext())),
        new StoreCredentialsProviderImpl());

    super.onCreate(savedInstanceState);
  }
}
