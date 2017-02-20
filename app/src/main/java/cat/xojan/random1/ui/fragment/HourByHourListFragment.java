package cat.xojan.random1.ui.fragment;

import android.app.DownloadManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import javax.inject.Inject;

import cat.xojan.random1.R;
import cat.xojan.random1.commons.ErrorUtil;
import cat.xojan.random1.databinding.RecyclerViewFragmentBinding;
import cat.xojan.random1.domain.entities.Podcast;
import cat.xojan.random1.domain.entities.Program;
import cat.xojan.random1.domain.entities.Section;
import cat.xojan.random1.domain.interactor.ProgramDataInteractor;
import cat.xojan.random1.injection.component.HomeComponent;
import cat.xojan.random1.ui.activity.BaseActivity;
import cat.xojan.random1.ui.adapter.PodcastListAdapter;
import cat.xojan.random1.viewmodel.PodcastsViewModel;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class HourByHourListFragment extends BaseFragment {

    public static final String TAG = HourByHourListFragment.class.getSimpleName();
    public static final String ARG_PROGRAM = "program_param";

    @Inject PodcastsViewModel mPodcastsViewModel;
    @Inject ProgramDataInteractor mProgramDataInteractor;
    @Inject DownloadManager mDownloadManager;

    private RecyclerViewFragmentBinding mBinding;
    private ActionBar mActionBar;
    private PodcastListAdapter mAdapter;
    private CompositeSubscription mSubscription = new CompositeSubscription();

    public static HourByHourListFragment newInstance(Program program) {
        Bundle args = new Bundle();
        args.putParcelable(ARG_PROGRAM, program);

        HourByHourListFragment podcastListFragment = new HourByHourListFragment();
        podcastListFragment.setArguments(args);

        return podcastListFragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        getComponent(HomeComponent.class).inject(this);
        mBinding = RecyclerViewFragmentBinding.inflate(inflater, container, false);

        mBinding.swiperefresh.setColorSchemeResources(R.color.colorAccent);
        mBinding.swiperefresh.setOnRefreshListener(() -> loadPodcasts(true));
        mBinding.recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        mAdapter = new PodcastListAdapter(getActivity(), mProgramDataInteractor, mDownloadManager);
        mBinding.recyclerView.setAdapter(mAdapter);

        return mBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mActionBar = ((BaseActivity) getActivity()).getSupportActionBar();
        showBackArrow(true);
        loadPodcasts(false);
        getActivity().setTitle(((Program) getArguments().get(ARG_PROGRAM)).getTitle());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (((Program) getArguments().get(ARG_PROGRAM)).getSections().size() > 1) {
            inflater.inflate(R.menu.hour_by_hour, menu);
        }
        menu.findItem(R.id.action_export_podcasts).setVisible(false);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                handleOnBackPressed();
                return true;
            case R.id.action_sections:
                showSections();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        mSubscription.add(mPodcastsViewModel.getDownloadedPodcastsUpdates()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::updateViewWithDownloaded));
    }

    @Override
    public void onPause() {
        super.onPause();
        mSubscription.clear();
        showBackArrow(false);
        getActivity().setTitle(getString(R.string.app_name));
    }

    @Override
    public boolean handleOnBackPressed() {
        getActivity().getSupportFragmentManager()
                .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        getActivity().setTitle(getString(R.string.app_name));
        ((BaseActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        setHasOptionsMenu(false);
        return true;
    }

    private void loadPodcasts(final boolean refresh) {
        new Handler().postDelayed(() -> {
            mBinding.swiperefresh.setRefreshing(true);
            Program program = getArguments().getParcelable(PodcastListFragment.ARG_PROGRAM);
            Section section = getArguments().getParcelable(PodcastListFragment.ARG_SECTION);

            mSubscription.add(mPodcastsViewModel.loadPodcasts(program, section, refresh)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::updateView,
                            this::handleError));
        }, 0);
    }

    private void handleError(Throwable throwable) {
        ErrorUtil.logException(throwable);
        mBinding.emptyList.setVisibility(View.VISIBLE);
    }

    private void updateView(List<Podcast> podcasts) {
        mBinding.emptyList.setVisibility(View.GONE);
        mBinding.swiperefresh.setRefreshing(false);
        mAdapter.update(podcasts);
    }

    private void updateViewWithDownloaded(List<Podcast> podcasts) {
        mAdapter.updateWithDownloaded(podcasts);
    }

    private void showBackArrow(boolean show) {
        setHasOptionsMenu(show);
        mActionBar.setDisplayHomeAsUpEnabled(show);
    }

    private void showSections() {
        mPodcastsViewModel.selectedSection(true);
        SectionFragment sectionListFragment = SectionFragment
                .newInstance((Program) getArguments().get(ARG_PROGRAM));
        ((BaseActivity) getActivity()).addFragment(sectionListFragment, SectionFragment.TAG, true);
    }
}
