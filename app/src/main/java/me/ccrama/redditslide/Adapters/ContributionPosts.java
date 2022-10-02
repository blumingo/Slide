package me.ccrama.redditslide.Adapters;

import android.content.Context;
import android.os.AsyncTask;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;
import net.dean.jraw.paginators.Sorting;
import net.dean.jraw.paginators.TimePeriod;
import net.dean.jraw.paginators.UserContributionPaginator;

import java.util.ArrayList;
import java.util.List;

import me.ccrama.redditslide.Authentication;
import me.ccrama.redditslide.BuildConfig;
import me.ccrama.redditslide.Fragments.SubmissionsView;
import me.ccrama.redditslide.HasSeen;
import me.ccrama.redditslide.PostLoader;
import me.ccrama.redditslide.PostMatch;
import me.ccrama.redditslide.SettingValues;
import me.ccrama.redditslide.SubmissionCache;
import me.ccrama.redditslide.Synccit.MySynccitReadTask;
import me.ccrama.redditslide.util.LogUtil;
import me.ccrama.redditslide.util.SortingUtil;

/**
 * Created by ccrama on 9/17/2015.
 */
public class ContributionPosts extends GeneralPosts implements PostLoader {
    protected final String where;
    protected final String subreddit;
    public boolean loading;
    private UserContributionPaginator paginator;
    protected SwipeRefreshLayout refreshLayout;
    protected ContributionAdapter adapter;
    Context c;
    public List<Submission> submissionPosts;
    public boolean error;

    public ContributionPosts(String subreddit, String where, Context c, SubmissionDisplay display) {
        submissionPosts = new ArrayList<>();
        this.subreddit = subreddit;
        this.where = where;
        this.c = c;
        new ShadowLoadData(true, display, c).execute(subreddit);
    }

    public ContributionPosts(String subreddit, String where) {
        this.subreddit = subreddit;
        this.where = where;
    }
    public ContributionPosts(String subreddit, String where, Context c) {
        submissionPosts = new ArrayList<>();
        this.subreddit = subreddit;
        this.where = where;
        this.c = c;
    }

    public void bindAdapter(ContributionAdapter a, SwipeRefreshLayout layout) {
        this.adapter = a;
        this.refreshLayout = layout;
        loadMore(a, subreddit, true);
    }

    public void loadMore(ContributionAdapter adapter, String subreddit, boolean reset) {
        new LoadData(reset).execute(subreddit);
    }

    @Override
    public void loadMore(Context context, SubmissionDisplay display, boolean reset) {
        new ShadowLoadData(reset, display, c).execute(subreddit);
    }

    @Override
    public List<Submission> getPosts() {
        return submissionPosts;
    }

    @Override
    public boolean hasMore() {
        return !nomore;
    }


    public long currentid;
    public SubmissionDisplay displayer;

    public class LoadData extends AsyncTask<String, Void, ArrayList<Contribution>> {
        final boolean reset;

        public LoadData(boolean reset) {
            this.reset = reset;
        }

        @Override
        public void onPostExecute(ArrayList<Contribution> submissions) {
            loading = false;

            if (submissions != null && !submissions.isEmpty()) {
                // new submissions found

                int start = 0;
                if (posts != null) {
                    start = posts.size() + 1;
                }


                if (reset || posts == null) {
                    posts = submissions;
                    start = -1;
                } else {
                    posts.addAll(submissions);
                }

                final int finalStart = start;
                // update online
                if (refreshLayout != null) {
                    refreshLayout.setRefreshing(false);
                }

                if (finalStart != -1) {
                    adapter.notifyItemRangeInserted(finalStart + 1, posts.size());
                } else {
                    adapter.notifyDataSetChanged();
                }

            } else if (submissions != null) {
                // end of submissions
                nomore = true;
                adapter.notifyDataSetChanged();

            } else if (!nomore) {
                // error
                adapter.setError(true);
            }
            refreshLayout.setRefreshing(false);
        }

        @Override
        protected ArrayList<Contribution> doInBackground(String... subredditPaginators) {
            ArrayList<Contribution> newData = new ArrayList<>();
            try {
                if (reset || paginator == null) {
                    paginator = new UserContributionPaginator(Authentication.reddit, where, subreddit);

                    paginator.setSorting(SortingUtil.getSorting(subreddit, Sorting.NEW));
                    paginator.setTimePeriod(SortingUtil.getTime(subreddit, TimePeriod.ALL));
                }

                if (!paginator.hasNext()) {
                    nomore = true;
                    return new ArrayList<>();
                }
                for (Contribution c : paginator.next()) {
                    if (c instanceof Submission) {
                        Submission s = (Submission) c;
                        if (!PostMatch.doesMatch(s)) {
                            newData.add(s);
                        }
                    } else {
                        newData.add(c);
                    }
                }

                HasSeen.setHasSeenContrib(newData);

                return newData;
            } catch (Exception e) {
                return null;
            }
        }

    }

    public class ShadowLoadData extends AsyncTask<String, Void, List<Submission>> {
        final boolean reset;
        Context context;
        public int start;

        public ShadowLoadData(boolean reset, SubmissionDisplay display, Context context) {
            this.reset = reset;
            displayer = display;
            this.context = context;
        }


        @Override
        public void onPreExecute() {
            if (reset) {
                submissionPosts.clear();
            }
        }

        @Override
        public void onPostExecute(List<Submission> submissions) {
            boolean success = true;
            loading = false;
            if (submissions != null && !submissions.isEmpty()) {
                if (displayer instanceof SubmissionsView && ((SubmissionsView) displayer).adapter.isError) {
                    ((SubmissionsView) displayer).adapter.undoSetError();
                }

                String[] ids = new String[submissions.size()];
                int i = 0;
                for (Submission s : submissions) {
                    ids[i] = s.getId();
                    i++;
                }
                displayer.updateSuccess(submissions, start);
                currentid = 0;
                if (!SettingValues.synccitName.isEmpty()) {
                    new MySynccitReadTask(displayer).execute(ids);
                }

            } else if (submissions != null) {
                // end of submissions
                nomore = true;
                displayer.updateSuccess(submissionPosts, submissionPosts.size() + 1);
            } else {
                if (!nomore) {
                    // error
                    LogUtil.v("Setting error");
                    success = false;
                }
            }
            ContributionPosts.this.error = !success;
        }


        @Override
        protected List<Submission> doInBackground(String... subredditPaginators) {
            if (BuildConfig.DEBUG) LogUtil.v("Loading data");

            if (reset || paginator == null) {
                nomore = false;
                paginator = new UserContributionPaginator(Authentication.reddit, where, subreddit);
                paginator.setSorting(SettingValues.getSubmissionSort(subreddit));
                paginator.setTimePeriod(SettingValues.getSubmissionTimePeriod(subreddit));
            }
            if (!paginator.hasNext()) {
                nomore = true;
                return new ArrayList<>();
            }
            List<Submission> newData = new ArrayList<>();
            for (Contribution c : paginator.next()) {
                if (c instanceof Submission) {
                    Submission s = (Submission) c;
                    if (!PostMatch.doesMatch(s)) {
                        newData.add(s);
                        submissionPosts.add(s);
                    }
                }
            }

            if (SettingValues.storeHistory) {
                HasSeen.setHasSeenSubmission(newData);
            }
            SubmissionCache.cacheSubmissions(newData, context, subreddit);

            if (reset || submissionPosts == null) {
                submissionPosts = new ArrayList<>(newData);
                start = -1;
            } else {
                submissionPosts.addAll(newData);
            }
            start = 0;
            if (submissionPosts != null) {
                start = submissionPosts.size() + 1;
            }

            return newData;
        }
    }

}
