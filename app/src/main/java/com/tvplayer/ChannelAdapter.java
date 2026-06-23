package com.tvplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.VH> {

    private List<MainActivity.ChannelItem> channels;
    private final java.util.function.Consumer<Integer> onClick;
    private int selected = -1;

    ChannelAdapter(List<MainActivity.ChannelItem> channels,
                   java.util.function.Consumer<Integer> onClick) {
        this.channels = channels;
        this.onClick = onClick;
    }

    void update(List<MainActivity.ChannelItem> list) {
        this.channels = list;
        this.selected = -1;
        notifyDataSetChanged();
    }

    void setSelected(int pos) {
        int old = selected;
        selected = pos;
        if (old >= 0) notifyItemChanged(old);
        if (pos >= 0) notifyItemChanged(pos);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_channel, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        MainActivity.ChannelItem ch = channels.get(position);
        String text = ch.urls.size() > 1 ? ch.name + " (" + ch.urls.size() + " sources)" : ch.name;
        holder.btn.setText(text);
        holder.btn.setSelected(position == selected);
        holder.btn.setOnClickListener(v -> {
            int p = holder.getAdapterPosition();
            if (p != RecyclerView.NO_POSITION && onClick != null) {
                onClick.accept(p);
            }
        });
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        Button btn;
        VH(@NonNull View itemView) {
            super(itemView);
            btn = itemView.findViewById(R.id.btnChannel);
        }
    }
}