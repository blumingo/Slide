package me.ccrama.redditslide.Activities;

import android.os.Bundle;
import android.widget.TextView;

import me.ccrama.redditslide.R;

public class SwipeInformation extends BaseActivity{
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.swipe_information);
        if(getIntent().hasExtra("subtitle")){
            ((TextView)findViewById(R.id.top)).setText(getIntent().getStringExtra("subtitle"));
        }
    }
}
