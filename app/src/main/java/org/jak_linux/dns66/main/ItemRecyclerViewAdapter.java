/* Copyright (C) 2016 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.main;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.jak_linux.dns66.Configuration;
import org.jak_linux.dns66.FileHelper;
import org.jak_linux.dns66.ItemChangedListener;
import org.jak_linux.dns66.MainActivity;
import org.jak_linux.dns66.R;

import java.util.List;

public class ItemRecyclerViewAdapter extends RecyclerView.Adapter<ItemRecyclerViewAdapter.ViewHolder> {
    public List<Configuration.Item> items;
    private Context context;

    public ItemRecyclerViewAdapter(List<Configuration.Item> items) {
        this.items = items;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ItemRecyclerViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        context = parent.getContext();
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_item, parent, false);

        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.item = items.get(position);
        holder.titleView.setText(items.get(position).title);
        holder.subtitleView.setText(items.get(position).location);
        switch (items.get(position).state) {
            case Configuration.Item.STATE_IGNORE:
                holder.iconView.setImageDrawable(context.getDrawable(R.drawable.ic_state_ignore));
                break;
            case Configuration.Item.STATE_DENY:
                holder.iconView.setImageDrawable(context.getDrawable(R.drawable.ic_state_deny));
                break;
            case Configuration.Item.STATE_ALLOW:
                holder.iconView.setImageDrawable(context.getDrawable(R.drawable.ic_state_allow));
                break;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public Configuration.Item item;
        public View view;
        public TextView titleView;
        public TextView subtitleView;
        public ImageView iconView;

        public ViewHolder(View view) {
            super(view);
            this.view = view;
            titleView = (TextView) view.findViewById(R.id.item_title);
            subtitleView = (TextView) view.findViewById(R.id.item_subtitle);
            iconView = (ImageView) view.findViewById(R.id.item_enabled);

            view.setOnClickListener(this);
            iconView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            final int position = getAdapterPosition();
            if (v == iconView) {
                item.state = (item.state + 1) % 3;
                ItemRecyclerViewAdapter.this.notifyItemChanged(position);
                FileHelper.writeSettings(itemView.getContext(), MainActivity.config);
            } else if (v == view) {
                // Start edit activity
                MainActivity main = (MainActivity) v.getContext();
                main.editItem(item, new ItemChangedListener() {
                            @Override
                            public void onItemChanged(Configuration.Item changedItem) {
                                items.set(position, changedItem);
                                ItemRecyclerViewAdapter.this.notifyItemChanged(position);
                                FileHelper.writeSettings(itemView.getContext(), MainActivity.config);
                            }
                        }
                );
            }
        }
    }
}