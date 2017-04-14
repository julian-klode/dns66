/* Copyright (C) 2016 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.main;

import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;

import org.jak_linux.dns66.FileHelper;
import org.jak_linux.dns66.MainActivity;

import java.util.Collections;

/**
 * Simple ItemTouchHelper callback for a collection based adapter.
 */
class ItemTouchHelperCallback extends ItemTouchHelper.SimpleCallback {
    private final ItemRecyclerViewAdapter mAdapter;

    public ItemTouchHelperCallback(ItemRecyclerViewAdapter mAdapter) {
        super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.mAdapter = mAdapter;
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        int a = viewHolder.getAdapterPosition();
        int b = target.getAdapterPosition();

        Collections.swap(mAdapter.items, a, b);
        mAdapter.notifyItemMoved(a, b);

        return true;
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
        mAdapter.items.remove(viewHolder.getAdapterPosition());
        mAdapter.notifyItemRemoved(viewHolder.getAdapterPosition());
    }

    public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        Log.d("ItemTouchHelperCallback", "clearView: Done with interaction. Saving settings.");
        FileHelper.writeSettings(viewHolder.itemView.getContext(), MainActivity.config);
    }

}
