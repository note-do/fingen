/*
 *  ******************************************************************************
 *     Copyright (c) 2013 Gabriele Mariotti.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *    *****************************************************************************
 */
package com.yoshione.fingen;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Xml;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows res/raw/changelog.xml as a plain list. Replaces the changeloglib library.
 */
public class FragmentChangelog extends DialogFragment {

    public FragmentChangelog() {
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ListView listView = (ListView) getActivity().getLayoutInflater().inflate(R.layout.fragment_changelog, null);
        listView.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, readChangelog(getActivity())));

        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.ttl_changelog)
                .setView(listView)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.dismiss();
                            }
                        }
                )
                .create();
    }

    private static List<CharSequence> readChangelog(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        int bulletIndent = Math.round(16 * density);
        int textIndent = Math.round(28 * density);

        List<CharSequence> lines = new ArrayList<>();
        try (InputStream in = context.getResources().openRawResource(R.raw.changelog)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, "UTF-8");
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    if ("changelogversion".equals(parser.getName())) {
                        String date = parser.getAttributeValue(null, "changeDate");
                        if (date != null && date.startsWith(">")) {
                            date = date.substring(1);
                        }
                        SpannableStringBuilder header = new SpannableStringBuilder(
                                parser.getAttributeValue(null, "versionName") + "   " + date);
                        header.setSpan(new StyleSpan(Typeface.BOLD), 0, header.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        header.setSpan(new RelativeSizeSpan(1.3f), 0, header.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        lines.add(header);
                    } else if ("changelogtext".equals(parser.getName())) {
                        SpannableString item = new SpannableString("\u2022 " + parser.nextText());
                        item.setSpan(new LeadingMarginSpan.Standard(bulletIndent, textIndent), 0, item.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        lines.add(item);
                    }
                }
                event = parser.next();
            }
        } catch (Exception e) {
            lines.add(e.getMessage());
        }
        return lines;
    }
}
