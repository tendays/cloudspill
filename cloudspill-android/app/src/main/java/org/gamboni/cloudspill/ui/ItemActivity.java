package org.gamboni.cloudspill.ui;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import org.gamboni.cloudspill.R;
import org.gamboni.cloudspill.domain.Domain;

/** Activity displaying a single image in full screen.
 *
 * @author tendays
 */
public class ItemActivity extends AppCompatActivity {

    private Domain domain;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_item);

        this.domain = new Domain(this);

        getIntent().getIntExtra();
    }
}
