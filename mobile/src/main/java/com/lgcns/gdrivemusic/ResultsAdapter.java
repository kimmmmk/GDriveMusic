/**
 * Created by cnsmin.kim on 2016-10-04.
 */

package com.lgcns.gdrivemusic;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.widget.DataBufferAdapter;

/**
 * A DataBufferAdapter to display the results of file listing/querying requests.
 */
public class ResultsAdapter extends DataBufferAdapter<Metadata> {
    private static final String TAG = "gdrive-result";

    public ResultsAdapter(Context context) {
        super(context, android.R.layout.simple_list_item_1);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = View.inflate(getContext(),
                    android.R.layout.simple_list_item_1, null);
        }
        Metadata metadata = getItem(position);
        TextView titleTextView =
                (TextView) convertView.findViewById(android.R.id.text1);
        titleTextView.setText(metadata.getTitle());
        Log.i(TAG, metadata.getTitle() + " : " + metadata.getMimeType());

        return convertView;
    }
}
