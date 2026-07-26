package com.hippo.ehviewer.ui.scene.gallery.list;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhTagDatabase;
import com.hippo.ehviewer.client.data.userTag.UserTag;
import com.hippo.ehviewer.client.data.userTag.UserTagList;
import com.hippo.ehviewer.subscription.UpdateBadgeFormatter;

public class SubscriptionItemAdapter extends BaseAdapter {

    private final UserTagList userTagList;

    private final LayoutInflater inflater;

    private final EhTagDatabase ehTags;

    public SubscriptionItemAdapter(Context context,UserTagList userTagList,EhTagDatabase ehTags){
        this.userTagList = userTagList;
        if (ehTags==null){
            this.ehTags = EhTagDatabase.getInstance(context);
        }else {
            this.ehTags = ehTags;
        }
        inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return userTagList.userTags.size();
    }

    @Override
    public UserTag getItem(int position) {
        return userTagList.userTags.get(position);
    }

    @Override
    public long getItemId(int position) {
        return Long.decode(getItem(position).userTagId.substring(8));
    }

    @SuppressLint({"ViewHolder","InflateParams"})
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        UserTag userTag = getItem(position);

        View view = inflater.inflate(R.layout.subscripition_list_item,null);
        TextView textView = view.findViewById(R.id.label);

        String name = userTag.getName(ehTags);
        textView.setText(UpdateBadgeFormatter.format(
                textView.getContext(), name, userTag.followCount));


        return view;
    }
}
