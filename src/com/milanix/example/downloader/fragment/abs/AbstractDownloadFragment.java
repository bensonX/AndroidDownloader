package com.milanix.example.downloader.fragment.abs;

import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.ListView;

import com.milanix.example.downloader.R;
import com.milanix.example.downloader.data.provider.DownloadContentProvider;
import com.milanix.example.downloader.dialog.DeleteDownloadDialog;
import com.milanix.example.downloader.dialog.DeleteDownloadDialog.OnDeleteDownloadListener;
import com.milanix.example.downloader.fragment.DownloadedFragment;
import com.milanix.example.downloader.fragment.adapter.DownloadListAdapter;
import com.milanix.example.downloader.util.ToastHelper;

/**
 * This is an abstract download framgnet
 * 
 * @author Milan
 * 
 */
public abstract class AbstractDownloadFragment extends AbstractFragment
		implements OnDeleteDownloadListener,
		LoaderManager.LoaderCallbacks<Cursor> {

	private static final int HANDLE_REFRESH_ADAPTER = 0;

	private static final String HANDLE_KEY_ISSILENT = "key_issilent";

	private DownloadContentObserver downloadContentObserver;

	protected View rootView;
	protected ListView downloading_list;
	protected DownloadListAdapter adapter;

	protected Handler uiUpdateHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case HANDLE_REFRESH_ADAPTER:
				if (null != msg && null != msg.getData()
						&& msg.getData().containsKey(HANDLE_KEY_ISSILENT))
					refreshCursorLoader(msg.getData().containsKey(
							HANDLE_KEY_ISSILENT));
				else
					refreshCursorLoader(false);

				break;
			default:
				super.handleMessage(msg);
			}
		}
	};

	public AbstractDownloadFragment() {
		super();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setHasOptionsMenu(true);
		setRetainInstance(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		rootView = inflater.inflate(R.layout.fragment_download, container,
				false);

		onInit();

		return rootView;
	}

	@Override
	public void onPause() {
		registerContentObserver();

		super.onPause();
	}

	@Override
	public void onResume() {
		unregisterContentObserver();

		super.onResume();
	}

	/**
	 * Called to init view components
	 */
	@Override
	protected void onInit() {
		super.onInit();

		setAdapter();
	}

	@Override
	protected void setUI() {
		downloading_list = (ListView) rootView
				.findViewById(R.id.downloading_list);
	}

	@Override
	protected void setListener() {
		downloading_list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
		downloading_list
				.setMultiChoiceModeListener(getMultiChoiceModeListener());
	}

	/**
	 * This method will set adapter and init loader
	 * 
	 * @param cursor
	 */
	protected void setAdapter() {
		adapter = new DownloadListAdapter(getActivity(), null, false);

		downloading_list.setAdapter(adapter);

		getLoaderManager().initLoader(0, null, this);
	}

	/**
	 * This method will register downloads content observer
	 */
	private void registerContentObserver() {
		if (null == downloadContentObserver)
			downloadContentObserver = new DownloadContentObserver(new Handler());

		getActivity().getContentResolver().registerContentObserver(
				DownloadContentProvider.CONTENT_URI_DOWNLOADS, true,
				downloadContentObserver);
	}

	/**
	 * This method will unregister downloads content observer
	 */
	private void unregisterContentObserver() {
		if (null != downloadContentObserver)
			getActivity().getContentResolver().unregisterContentObserver(
					downloadContentObserver);
	}

	/**
	 * This method will post refresh cursor loader to the handler.
	 * 
	 * @param isSilent
	 *            if refresh should be silent
	 */
	protected void postRefreshCursorLoader(boolean isSilent) {
		Bundle data = new Bundle();
		data.putBoolean(HANDLE_KEY_ISSILENT, isSilent);

		Message msg = Message.obtain(null, HANDLE_REFRESH_ADAPTER);
		msg.setData(data);

		if (null != uiUpdateHandler)
			uiUpdateHandler.sendMessage(msg);
	}

	/**
	 * This method will restart a cursor loader
	 * 
	 * @param isSilent
	 *            if false will notify user otherwise nothing will be displayed
	 */
	protected void refreshCursorLoader(boolean isSilent) {
		if (null != adapter) {
			getLoaderManager().restartLoader(0, null, this);

			if (!isSilent)
				ToastHelper.showToast(getActivity(), "Refreshing list");
		}
	}

	@Override
	public void onDownloadDeleted(boolean isSuccess) {
		if (isSuccess) {
			refreshCursorLoader(true);

			ToastHelper.showToast(getActivity(),
					getString(R.string.download_delete_success));
		} else
			ToastHelper.showToast(getActivity(),
					getString(R.string.download_delete_fail));
	}

	/**
	 * This method will show add new download dialog
	 * 
	 * downloadIds array of ids to delete
	 */
	protected void showRemoveDialog(long[] downloadIds) {
		Bundle bundle = new Bundle();
		bundle.putLongArray(DeleteDownloadDialog.KEY_DOWNLOADIDS, downloadIds);

		DeleteDownloadDialog newFragment = new DeleteDownloadDialog();
		newFragment.setArguments(bundle);
		newFragment.setTargetFragment(this, -1);
		newFragment.setCancelable(true);
		newFragment.show(getFragmentManager(),
				DeleteDownloadDialog.class.getSimpleName());
	}

	/**
	 * This method will remove given ids from the database
	 * 
	 * @param downloadIds
	 *            array of ids to delete
	 */
	protected void removeDownloads(long[] downloadIds) {
		showRemoveDialog(downloadIds);
	}

	@Override
	public String getLogTag() {
		return DownloadedFragment.class.getSimpleName();
	}

	@Override
	public abstract Loader<Cursor> onCreateLoader(int id, Bundle args);

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor newCursor) {
		adapter.swapCursor(newCursor);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		adapter.swapCursor(null);
	}

	/**
	 * This method will return MultiChoiceModeListener
	 * 
	 * @return MultiChoiceModeListener
	 */
	protected abstract MultiChoiceModeListener getMultiChoiceModeListener();

	/**
	 * Content observer for {@link DownloadContentProvider}
	 * 
	 * @author Milan
	 * 
	 */
	protected class DownloadContentObserver extends ContentObserver {

		public DownloadContentObserver(Handler handler) {
			super(handler);
		}

		@Override
		public void onChange(boolean selfChange, Uri uri) {
			postRefreshCursorLoader(false);
		}

		@Override
		public void onChange(boolean selfChange) {
			onChange(selfChange, null);
		}

	}

}