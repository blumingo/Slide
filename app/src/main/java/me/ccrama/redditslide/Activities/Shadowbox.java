package me.ccrama.redditslide.Activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import net.dean.jraw.models.Submission;

import java.util.HashMap;
import java.util.List;

import me.ccrama.redditslide.Adapters.ContributionPosts;
import me.ccrama.redditslide.Adapters.MultiredditPosts;
import me.ccrama.redditslide.Adapters.SubmissionDisplay;
import me.ccrama.redditslide.Adapters.SubredditPosts;
import me.ccrama.redditslide.Authentication;
import me.ccrama.redditslide.ContentType;
import me.ccrama.redditslide.Fragments.AlbumFull;
import me.ccrama.redditslide.Fragments.MediaFragment;
import me.ccrama.redditslide.Fragments.SelftextFull;
import me.ccrama.redditslide.Fragments.TitleFull;
import me.ccrama.redditslide.Fragments.TumblrFull;
import me.ccrama.redditslide.HasSeen;
import me.ccrama.redditslide.LastComments;
import me.ccrama.redditslide.OfflineSubreddit;
import me.ccrama.redditslide.PostLoader;
import me.ccrama.redditslide.R;
import me.ccrama.redditslide.SettingValues;
import me.ccrama.redditslide.util.AsyncCallback;

/**
 * Created by ccrama on 9/17/2015.
 */
public class Shadowbox extends FullScreenActivity implements SubmissionDisplay, AsyncCallback {
    public static final String EXTRA_PROFILE = "profile";
    public static final String EXTRA_PAGE = "page";
    public static final String EXTRA_SUBREDDIT = "subreddit";
    public static final String EXTRA_MULTIREDDIT = "multireddit";
    public static final String EXTRA_WHERE = "where";
    public PostLoader subredditPosts;
    public String subreddit;
    public String profile;
    public String where;
    int firstPage;
    private int count;

    ShadowboxPagerAdapter submissionsPager;

    @Override
    public void onBackPressed() {
        Fragment mCurrentFragment = submissionsPager.hashMap.get(pager.getCurrentItem());
        if (mCurrentFragment instanceof MediaFragment) {
            MediaFragment mediaFragment = (MediaFragment) mCurrentFragment;
            if (mediaFragment.slideLayout.getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED) {
                mediaFragment.slideLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
            }
        } else {
            super.onBackPressed();
        }
    }

    public ViewPager2 pager;

    @Override
    public void onCreate(Bundle savedInstance) {
        overrideSwipeFromAnywhere();

        subreddit = getIntent().getExtras().getString(EXTRA_SUBREDDIT);

        firstPage = getIntent().getExtras().getInt(EXTRA_PAGE, 0);
        subreddit = getIntent().getExtras().getString(EXTRA_SUBREDDIT);
        String multireddit = getIntent().getExtras().getString(EXTRA_MULTIREDDIT);
        profile = getIntent().getExtras().getString(EXTRA_PROFILE, "");
        where = getIntent().getExtras().getString(EXTRA_WHERE, "submitted");
        boolean isProfile = false;
        if (multireddit != null) {
            subredditPosts = new MultiredditPosts(multireddit, profile);
        } else if (!profile.isEmpty()) {
            isProfile = true;
            subredditPosts = new ContributionPosts(profile, where, Shadowbox.this, Shadowbox.this);
        } else {
            subredditPosts = new SubredditPosts(subreddit, Shadowbox.this);
        }

        subreddit = multireddit == null ? subreddit : ("multi" + multireddit);

        if (multireddit == null && profile.isEmpty()) {
            setShareUrl("https://reddit.com/r/" + subreddit);
        }

        applyDarkColorTheme(subreddit);
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_slide);


        if (!isProfile) {
            long offline = getIntent().getLongExtra("offline", 0L);
            OfflineSubreddit submissions = OfflineSubreddit.getSubreddit(subreddit, offline, !Authentication.didOnline, this);
            subredditPosts.getPosts().addAll(submissions.submissions);
        }
        count = subredditPosts.getPosts().size();

        pager = (ViewPager2) findViewById(R.id.content_view);
        submissionsPager = new ShadowboxPagerAdapter(this);
        pager.setAdapter(submissionsPager);
        pager.setCurrentItem(firstPage);
        //pager.setOrientation(ORIENTATION_VERTICAL);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (SettingValues.storeHistory) {
                    if (subredditPosts.getPosts().get(position).isNsfw() && !SettingValues.storeNSFWHistory) {
                        return;
                    }
                    HasSeen.addSeen(subredditPosts.getPosts().get(position).getFullName());
                }
            }
        });

    }

    @Override
    public void updateSuccess(final List<Submission> submissions, final int startIndex) {
        if (SettingValues.storeHistory) LastComments.setCommentsSince(submissions);
        runOnUiThread(() -> {
            count = subredditPosts.getPosts().size();
            if (startIndex != -1) {
                // TODO determine correct behaviour
                //comments.notifyItemRangeInserted(startIndex, posts.posts.size());
                submissionsPager.notifyDataSetChanged();
            } else {
                submissionsPager.notifyDataSetChanged();
            }

        });
    }

    @Override
    public void updateOffline(List<Submission> submissions, final long cacheTime) {
        runOnUiThread(() -> {
            count = subredditPosts.getPosts().size();
            submissionsPager.notifyDataSetChanged();
        });
    }

    @Override
    public void updateOfflineError() {
    }

    @Override
    public void updateError() {
    }

    @Override
    public void updateViews() {

    }

    @Override
    public void onAdapterUpdated() {
        submissionsPager.notifyDataSetChanged();
    }

    @Override
    public void completed() {
        if (subredditPosts.getPosts().size() == 0) {
            Intent i = new Intent(this, SwipeInformation.class);
            i.putExtra("subtitle", where);
            startActivityForResult(i, 333);
            finish();
        }

    }


    private class ShadowboxPagerAdapter extends FragmentStateAdapter {
        private final HashMap<Integer, Fragment> hashMap = new HashMap<>();

        ShadowboxPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int i) {
            Fragment f = null;
            ContentType.Type t = ContentType.getContentType(subredditPosts.getPosts().get(i));

            if (subredditPosts.getPosts().size() - 3 <= i && subredditPosts.hasMore()) {
                subredditPosts.loadMore(Shadowbox.this.getApplicationContext(), Shadowbox.this, false);
            }
            switch (t) {
                case GIF:
                case IMAGE:
                case IMGUR:
                case REDDIT:
                case EXTERNAL:
                case SPOILER:
                case DEVIANTART:
                case EMBEDDED:
                case XKCD:
                case REDDIT_GALLERY:
                case VREDDIT_DIRECT:
                case VREDDIT_REDIRECT:
                case LINK:
                case STREAMABLE:
                case VIDEO: {
                    f = new MediaFragment();
                    Bundle args = new Bundle();
                    Submission submission = subredditPosts.getPosts().get(i);
                    String previewUrl = "";
                    if (t != ContentType.Type.XKCD && submission.getDataNode().has("preview") && submission.getDataNode()
                            .get("preview")
                            .get("images")
                            .get(0)
                            .get("source")
                            .has("height")) { //Load the preview image which has probably already been cached in memory instead of the direct link
                        previewUrl = submission.getDataNode()
                                .get("preview")
                                .get("images")
                                .get(0)
                                .get("source")
                                .get("url")
                                .asText();
                    }
                    args.putString("contentUrl", submission.getUrl());
                    args.putString("firstUrl", previewUrl);
                    args.putInt("page", i);
                    args.putString("sub", subreddit);
                    f.setArguments(args);
                }
                break;
                case SELF:
                case NONE: {
                    if (subredditPosts.getPosts().get(i).getSelftext().isEmpty()) {
                        f = new TitleFull();
                    } else {
                        f = new SelftextFull();
                    }
                    Bundle args = new Bundle();
                    args.putInt("page", i);
                    args.putString("sub", subreddit);
                    f.setArguments(args);
                }
                break;
                case TUMBLR: {
                    f = new TumblrFull();
                    Bundle args = new Bundle();
                    args.putInt("page", i);
                    args.putString("sub", subreddit);
                    f.setArguments(args);
                }
                break;
                case ALBUM: {
                    f = new AlbumFull();
                    Bundle args = new Bundle();
                    args.putInt("page", i);
                    args.putString("sub", subreddit);
                    f.setArguments(args);
                }
                break;
            }
            hashMap.put(i, f);
            return f;
        }

        @Override
        public int getItemCount() {
            return count;
        }
    }
}
