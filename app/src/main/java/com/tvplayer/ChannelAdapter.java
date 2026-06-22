package com.tvplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {

    private List<MainActivity.Channel> list;
    private final java.util.function.Consumer<MainActivity.Channel> onClick;

    ChannelAdapter(List<MainActivity.Channel> list,
                   java.util.function.Consumer<MainActivity.Channel> onClick) {
        this.list = list;
        this.onClick = onClick;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_channel, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MainActivity.Channel channel = list.get(position);
        holder.name.setText(channel.name);
        holder.itemView.setOnClickListener(v -> {
            if (onClick != null) onClick.accept(channel);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.channelName);
        }
    }
}
