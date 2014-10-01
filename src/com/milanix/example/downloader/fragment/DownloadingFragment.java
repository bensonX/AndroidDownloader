package com.milanix.example.downloader.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;

import com.milanix.example.downloader.R;
import com.milanix.example.downloader.data.dao.Download;
import com.milanix.example.downloader.data.dao.Download.DownloadListener;
import com.milanix.example.downloader.data.dao.Download.DownloadState;
import com.milanix.example.downloader.data.dao.Download.FailedReason;
import com.milanix.example.downloader.data.dao.Download.TaskState;
import com.milanix.example.downloader.data.database.DownloadsDatabase;
import com.milanix.example.downloader.data.database.util.QueryHelper;
import com.milanix.example.downloader.data.provider.DownloadContentProvider;
import com.milanix.example.downloader.dialog.AddNewDownloadDialog;
import com.milanix.example.downloader.dialog.AddNewDownloadDialog.OnAddNewDownloadListener;
import com.milanix.example.downloader.fragment.abs.AbstractDownloadFragment;
import com.milanix.example.downloader.fragment.adapter.DownloadListAdapter;
import com.milanix.example.downloader.service.DownloadService;
import com.milanix.example.downloader.service.DownloadService.DownloadBinder;
import com.milanix.example.downloader.service.DownloadService.TaskStateResult;
import com.milanix.example.downloader.util.NetworkUtils;
import com.milanix.example.downloader.util.PreferenceHelper;
import com.milanix.example.downloader.util.ToastHelper;

/**
 * This fragment contains downloading list and its related logic.
 */
public class DownloadingFragment extends AbstractDownloadFragment implements
		OnAddNewDownloadListener {

	private DownloadService downloadService = null;

	private boolean bound = false;

	private ServiceConnection connection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			downloadService = ((DownloadBinder) service).getService();

			bound = true;

			setAdapter();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			bound = false;
		}
	};

	@Override
	protected void onInit() {
		setUI();
		setListener();

	}

	@Override
	public void setListener() {
		super.setListener();
	}

	@Override
	protected void setAdapter() {
		downloadListAdapter = new DownloadListAdapter(getActivity(), null,
				false, downloadService);

		downloading_list.setAdapter(downloadListAdapter);

		getLoaderManager().initLoader(0, null, this);
	}

	/**
	 * This method will resume downloads
	 * 
	 * @param selectedIds
	 */
	private void resumeDownloads(long[] selectedIds) {
		if (bound) {
			TaskStateResult result = DownloadService
					.resumeDownload(selectedIds);

			if (null != result && null != result.getFreshTasks()
					&& !result.getFreshTasks().isEmpty()) {
				ToastHelper.showToast(
						getActivity(),
						getResources().getQuantityString(
								R.plurals.download_fresh_added,
								result.getFreshTasks().size(),
								result.getFreshTasks().size()));
			}
		}
	}

	/**
	 * This method will pause downloads
	 */
	private void pauseDownloads(long[] selectedIds) {
		if (bound) {
			DownloadService.pauseDownload(selectedIds);
		}
	}

	@Override
	public MultiChoiceModeListener getMultiChoiceModeListener() {
		return new MultiChoiceModeListener() {

			@Override
			public boolean onActionItemClicked(android.view.ActionMode mode,
					MenuItem item) {
				switch (item.getItemId()) {
				case R.id.action_resume:
					resumeDownloads(downloading_list.getCheckedItemIds());

					mode.finish();

					return true;
				case R.id.action_pause:
					pauseDownloads(downloading_list.getCheckedItemIds());

					mode.finish();

					return true;
				case R.id.action_delete:
					removeDownloads(downloading_list.getCheckedItemIds());

					mode.finish();

					return true;
				default:
					return false;
				}
			}

			@Override
			public boolean onCreateActionMode(android.view.ActionMode mode,
					Menu menu) {
				MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.menu_context_downloading, menu);
				return true;
			}

			@Override
			public void onDestroyActionMode(android.view.ActionMode mode) {

			}

			@Override
			public boolean onPrepareActionMode(android.view.ActionMode mode,
					Menu menu) {
				return false;
			}

			@Override
			public void onItemCheckedStateChanged(android.view.ActionMode mode,
					int position, long id, boolean checked) {

			}
		};
	}

	/**
	 * This method will show add new download dialog
	 */
	private void showAddNewDialog() {
		DialogFragment newFragment = new AddNewDownloadDialog();
		newFragment.setTargetFragment(this, -1);
		newFragment.setCancelable(true);
		newFragment.show(getFragmentManager(),
				AddNewDownloadDialog.class.getSimpleName());
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		downloadListAdapter.setExpanded(position);

		//refreshCursorLoader(true);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);

		inflater.inflate(R.menu.menu_downloading, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
		case R.id.action_add:
			showAddNewDialog();

			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onNewDownloadAdded(Integer id) {
		if (null != id) {
			Download addedDownload = new Download().retrieve(getActivity(), id);

			if (null != addedDownload && null != addedDownload.getUrl()) {
				refreshCursorLoader(false);

				ToastHelper.showToast(getActivity(), String.format(
						getString(R.string.download_add_success),
						addedDownload.getUrl()));

				if (NetworkUtils.isNetworkConnected(getActivity()))
					pushDownloadToService(addedDownload);
				else
					ToastHelper.showToast(getActivity(),
							getString(R.string.download_disconnected));
			} else {
				ToastHelper.showToast(getActivity(),
						getString(R.string.download_add_fail));
			}

		} else
			ToastHelper.showToast(getActivity(),
					getString(R.string.download_add_fail));
	}

	/**
	 * This method will push download to the service
	 * 
	 * @param download
	 */
	private void pushDownloadToService(Download download) {
		if (bound) {
			downloadService.attachCallback(download.getId(),
					new DownloadListener() {

						@Override
						public void onDownloadStarted(Download download) {
							Log.d(getLogTag(),
									"Download started " + download.getName());

							postRefreshCursorLoader(false);
						}

						@Override
						public void onDownloadCancelled(Download download) {
							Log.d(getLogTag(),
									"Download cancelled " + download.getName());

							postRefreshCursorLoader(false);
						}

						@Override
						public void onDownloadCompleted(Download download) {
							Log.d(getLogTag(),
									"Download completed " + download.getName());

							postRefreshCursorLoader(false);
						}

						@Override
						public void onDownloadFailed(Download download,
								FailedReason reason) {
							Log.d(getLogTag(),
									"Download failed " + download.getName());

							postRefreshCursorLoader(false);
						}

						@Override
						public void onDownloadProgress(TaskState taskState,
								Download download, Integer progress) {
							if (TaskState.PAUSED.equals(taskState))
								Log.d(getLogTag(), "Download paused "
										+ download.getName());
						}
					});

			DownloadService.downloadFile(download.getId());
		}
	}

	@Override
	public void onStart() {
		super.onStart();

		getActivity().bindService(
				new Intent(getActivity(), DownloadService.class), connection,
				Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onStop() {
		super.onStop();

		if (bound) {
			getActivity().unbindService(connection);

			bound = false;
		}
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		return new CursorLoader(getActivity(),
				DownloadContentProvider.CONTENT_URI_DOWNLOADS, null,
				QueryHelper.getWhere(DownloadsDatabase.COLUMN_STATE,
						DownloadState.COMPLETED.toString(), false), null,
				QueryHelper.getOrdering(
						PreferenceHelper.getSortOrderingField(getActivity()),
						PreferenceHelper.getSortOrderingType(getActivity())));
	}

}