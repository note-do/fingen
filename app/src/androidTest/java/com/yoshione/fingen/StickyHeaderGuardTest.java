package com.yoshione.fingen;

import android.graphics.Canvas;
import android.graphics.Bitmap;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.adapter.AdapterTransactions;
import com.yoshione.fingen.widgets.StickyHeaderDecoration;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Sticky header lookup must not throw for positions outside of the adapter list
 * (stale positions while the list is cleared, empty list).
 */
@RunWith(AndroidJUnit4.class)
public class StickyHeaderGuardTest {

    @Rule
    public final SystemDialogsRule dialogs = new SystemDialogsRule();

    private ActivityMain mHost;

    @Before
    public void setUp() throws Exception {
        mHost = (ActivityMain) ScreenTestUtils.launch(ActivityMain.class, null);
    }

    @After
    public void tearDown() {
        ScreenTestUtils.finish(mHost);
    }

    @Test
    public void emptyAdapterDoesNotThrow() throws Throwable {
        final Throwable[] error = {null};
        final boolean[] hasHeader = {true};
        final long[] ids = new long[3];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    AdapterTransactions adapter = new AdapterTransactions(null, null, mHost);
                    adapter.addTransactions(new ArrayList<com.yoshione.fingen.model.Transaction>(), true);
                    ids[0] = adapter.getHeaderId(0);
                    ids[1] = adapter.getHeaderId(1);
                    ids[2] = adapter.getHeaderId(-1);

                    StickyHeaderDecoration decoration = new StickyHeaderDecoration(adapter);
                    Method m = StickyHeaderDecoration.class.getDeclaredMethod("hasHeader", int.class);
                    m.setAccessible(true);
                    hasHeader[0] = (Boolean) m.invoke(decoration, 1);

                    RecyclerView rv = new RecyclerView(mHost);
                    rv.setLayoutManager(new LinearLayoutManager(mHost));
                    rv.setAdapter(adapter);
                    rv.addItemDecoration(decoration);
                    rv.measure(android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY));
                    rv.layout(0, 0, 400, 400);
                    rv.draw(new Canvas(Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)));
                } catch (Throwable t) {
                    error[0] = t;
                }
            }
        });
        if (error[0] != null) {
            throw error[0];
        }
        assertEquals(RecyclerView.NO_ID, ids[0]);
        assertEquals(RecyclerView.NO_ID, ids[1]);
        assertEquals(RecyclerView.NO_ID, ids[2]);
        assertFalse(hasHeader[0]);
    }
}
