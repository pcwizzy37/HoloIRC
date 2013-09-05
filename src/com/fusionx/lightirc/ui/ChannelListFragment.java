package com.fusionx.lightirc.ui;

import android.animation.LayoutTransition;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;

import com.fusionx.lightirc.R;
import com.fusionx.lightirc.adapters.SelectionAdapter;
import com.fusionx.lightirc.interfaces.IServerSettings;
import com.fusionx.lightirc.ui.dialogbuilder.ChannelNamePromptDialogBuilder;
import com.fusionx.lightirc.util.MultiSelectionUtils;
import com.fusionx.lightirc.util.SharedPreferencesUtils;
import com.fusionx.lightirc.util.UIUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static com.fusionx.lightirc.constants.PreferenceConstants.AutoJoin;

public class ChannelListFragment extends ListFragment implements AdapterView
        .OnItemClickListener, MultiSelectionUtils.MultiChoiceModeListener {
    private SelectionAdapter<String> adapter;
    private boolean modeStarted = false;
    private MultiSelectionUtils.Controller mMultiSelectionController;
    private IServerSettings mCallbacks;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mCallbacks = (IServerSettings) activity;
        } catch (ClassCastException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (UIUtils.hasHoneycomb()) {
            getListView().setLayoutTransition(new LayoutTransition());
        }
    }

    @Override
    public boolean onCreateActionMode(final ActionMode mode, Menu menu) {
        final MenuInflater inflate = mode.getMenuInflater();
        inflate.inflate(R.menu.activty_server_settings_cab, menu);
        modeStarted = true;
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        menu.getItem(0).setVisible(!(getListView().getCheckedItemCount() > 1));
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        final ArrayList<String> positions = adapter.getSelectedItems();

        switch (item.getItemId()) {
            case R.id.activity_server_settings_cab_edit:
                final String edited = adapter.getItem(0);
                final ChannelNamePromptDialogBuilder dialog = new ChannelNamePromptDialogBuilder
                        (getActivity(), edited) {
                    @Override
                    public void onOkClicked(final String input) {
                        adapter.remove(edited);
                        adapter.add(input);
                    }
                };
                dialog.show();

                mode.finish();
                return true;
            case R.id.activity_server_settings_cab_delete:
                for (String selected : positions) {
                    adapter.remove(selected);
                }
                mode.finish();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position,
                                          long id, boolean checked) {
        mode.invalidate();

        adapter.toggleSelection(position);

        int selectedItemCount = adapter.getSelectedItemCount();
        if (selectedItemCount != 0) {
            final String quantityString = getResources().getQuantityString(R.plurals
                    .channel_selection, selectedItemCount, selectedItemCount);
            mode.setTitle(quantityString);
        }
    }

    @Override
    public void onDestroyActionMode(ActionMode arg0) {
        adapter.clearSelection();
        modeStarted = false;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getListView().setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflate) {
        inflate.inflate(R.menu.activity_server_settings_channellist_ab, menu);
        super.onCreateOptionsMenu(menu, inflate);
    }

    @Override
    public View onCreateView(final LayoutInflater inflate, final ViewGroup container,
                             final Bundle savedInstanceState) {
        final View rootView = super.onCreateView(inflate, container, savedInstanceState);
        adapter = new SelectionAdapter<>(getActivity(), new TreeSet<String>());

        final SharedPreferences settings = getActivity()
                .getSharedPreferences(mCallbacks.getFileName(), Context.MODE_PRIVATE);
        final Set<String> set = SharedPreferencesUtils.getStringSet(settings, AutoJoin,
                new HashSet<String>());
        for (final String channel : set) {
            adapter.add(channel);
        }

        setListAdapter(adapter);
        setHasOptionsMenu(true);

        mMultiSelectionController = MultiSelectionUtils.attachMultiSelectionController(
                (ListView) rootView.findViewById(android.R.id.list),
                (ActionBarActivity) getActivity(), this);

        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mMultiSelectionController != null) {
            mMultiSelectionController.finish();
            mMultiSelectionController = null;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mMultiSelectionController != null) {
            mMultiSelectionController.saveInstanceState(outState);
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void setMenuVisibility(boolean menuVisible) {
        super.setMenuVisibility(menuVisible);

        if (mMultiSelectionController == null) {
            return;
        }

        // Hide the action mode when the fragment becomes invisible
        if (!menuVisible) {
            Bundle bundle = new Bundle();
            if (mMultiSelectionController.saveInstanceState(bundle)) {
                mMultiSelectionController.finish();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case R.id.activity_server_settings_ab_add:
                final ChannelNamePromptDialogBuilder dialog =
                        new ChannelNamePromptDialogBuilder(getActivity()) {
                            @Override
                            public void onOkClicked(final String input) {
                                adapter.add(input);
                            }
                        };
                dialog.show();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onPause() {
        SharedPreferencesUtils.putStringSet(getActivity().getSharedPreferences(mCallbacks
                .getFileName(), Context.MODE_PRIVATE), AutoJoin, adapter.getCopyOfItems());

        super.onPause();
    }

    @Override
    public void onItemClick(final AdapterView<?> adapterView, final View view, final int i,
                            final long l) {
        if (!modeStarted) {
            ((ActionBarActivity) getActivity()).startSupportActionMode(this);
        }
        final boolean checked = adapter.getSelectedItems().contains(adapter.getItem(i));
        getListView().setItemChecked(i, !checked);
    }
}