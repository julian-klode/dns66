/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
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
    public final List<Configuration.Item> items;
    private final int stateChoices;
    private Context context;

    public ItemRecyclerViewAdapter(List<Configuration.Item> items, int stateChoices) {
        this.items = items;
        this.stateChoices = stateChoices;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ItemRecyclerViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        context = parent.getContext();
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_item, parent, false);

        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.item = items.get(position);
        holder.titleView.setText(items.get(position).title);
        holder.subtitleView.setText(items.get(position).location);

        holder.updateState();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public final View view;
        public final TextView titleView;
        public final TextView subtitleView;
        public final ImageView iconView;
        public Configuration.Item item;

        public ViewHolder(View view) {
            super(view);
            this.view = view;
            titleView = (TextView) view.findViewById(R.id.item_title);
            subtitleView = (TextView) view.findViewById(R.id.item_subtitle);
            iconView = (ImageView) view.findViewById(R.id.item_enabled);

            view.setOnClickListener(this);
            iconView.setOnClickListener(this);
        }

        void updateState() {
            iconView.setImageAlpha(255 * 87 / 100);
            if (stateChoices == 2) {
                switch (item.state) {
                    case Configuration.Item.STATE_IGNORE:
                    case Configuration.Item.STATE_DENY:
                        iconView.setImageDrawable(context.getDrawable(R.drawable.ic_check_box_outline_blank_black_24dp));
                        iconView.setContentDescription(context.getString(R.string.do_not_use_dns_server));
                        break;
                    case Configuration.Item.STATE_ALLOW:
                        iconView.setImageDrawable(context.getDrawable(R.drawable.ic_check_box_black_24dp));
                        iconView.setContentDescription(context.getString(R.string.use_dns_server));
                        break;
                }
            } else {
                switch (item.state) {
                    case Configuration.Item.STATE_IGNORE:
                        iconView.setImageDrawable(context.getDrawable(R.drawable.ic_state_ignore));
                        iconView.setImageAlpha(255 * 38 / 100);
                        break;
                    case Configuration.Item.STATE_DENY:
                        iconView.setImageDrawable(context.getDrawable(R.drawable.ic_state_deny));
                        break;
                    case Configuration.Item.STATE_ALLOW:
                        iconView.setImageDrawable(context.getDrawable(R.drawable.ic_state_allow));
                        break;
                }
                iconView.setContentDescription(context.getResources().getStringArray(R.array.item_states)[item.state]);
            }

        }

        @Override
        public void onClick(View v) {
            final int position = getAdapterPosition();
            if (v == iconView) {
                item.state = (item.state + 1) % stateChoices;
                updateState();
                FileHelper.writeSettings(itemView.getContext(), MainActivity.config);
            } else if (v == view) {
                // Start edit activity
                MainActivity main = (MainActivity) v.getContext();
                main.editItem(stateChoices, item, new ItemChangedListener() {
                            @Override
                            public void onItemChanged(Configuration.Item changedItem) {
                                if (changedItem == null) {
                                    items.remove(position);
                                    notifyItemRemoved(position);
                                } else {
                                    items.set(position, changedItem);
                                    ItemRecyclerViewAdapter.this.notifyItemChanged(position);
                                }
                                FileHelper.writeSettings(itemView.getContext(), MainActivity.config);
                            }
                        }
                );
            }
        }
    }
}