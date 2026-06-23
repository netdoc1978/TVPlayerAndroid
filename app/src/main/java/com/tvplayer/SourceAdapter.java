package com.tvplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SourceAdapter extends RecyclerView.Adapter<SourceAdapter.VH> {

    private List<MainActivity.SourceItem> sources;
    private final java.util.function.Consumer<Integer> onClick;

    SourceAdapter(List<MainActivity.SourceItem> sources,
                  java.util.function.Consumer<Integer> onClick) {
        this.sources = sources;
        this.onClick = onClick;
    }

    void update(List<MainActivity.SourceItem> list) {
        this.sources = list;
        notifyDataSetChanged();
    }

    MainActivity.SourceItem getItem(int pos) {
        if (pos >= 0 && pos < sources.size()) return sources.get(pos);
        return null;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_source, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        MainActivity.SourceItem src = sources.get(position);
        holder.btn.setText(src.label);
        holder.btn.setOnClickListener(v -> {
            int p = holder.getAdapterPosition();
            if (p != RecyclerView.NO_POSITION && onClick != null) {
                onClick.accept(p);
            }
        });
    }

    @Override
    public int getItemCount() {
        return sources.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        Button btn;
        VH(@NonNull View itemView) {
            super(itemView);
            btn = itemView.findViewById(R.id.btnSource);
        }
    }
}