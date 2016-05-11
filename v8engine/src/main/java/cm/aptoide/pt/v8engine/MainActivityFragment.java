/*
 * Copyright (c) 2016.
 * Modified by Neurophobic Animal on 10/05/2016.
 */

package cm.aptoide.pt.v8engine;

import android.support.v4.app.Fragment;

import java.util.concurrent.atomic.AtomicInteger;

import cm.aptoide.pt.dataprovider.ws.v7.store.StoreContext;
import cm.aptoide.pt.v8engine.activities.AptoideSimpleFragmentActivity;
import cm.aptoide.pt.v8engine.fragment.implementations.HomeFragment;

/**
 * Created by neuro on 06-05-2016.
 */
public class MainActivityFragment extends AptoideSimpleFragmentActivity {

	@Override
	protected Fragment createFragment() {
		return HomeFragment
				.newInstance(V8Engine.getConfiguration().getDefaultStore(), StoreContext.home);
	}

	private AtomicInteger atomicInt = new AtomicInteger(0);

	public void showFragment(Fragment fragment) {
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.fragment_placeholder, fragment)
				.addToBackStack("fragment_" + atomicInt.incrementAndGet())
				.commit();
	}
}
