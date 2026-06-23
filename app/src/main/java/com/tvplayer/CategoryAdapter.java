package com.tvplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {

    private List<String> categories;
    private final java.util.function.Consumer<Integer> onClick;
    private int selected = -1;

    CategoryAdapter(List<String> categories, java.util.function.Consumer<Integer> onClick) {
        this.categories = categories;
        this.onClick = onClick;
    }

    void update(List<String> list) {
        this.categories = list;
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
            .inflate(R.layout.item_category, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        String cat = categories.get(position);
        holder.btn.setText(cat);
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
        return categories.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        Button btn;
        VH(@NonNull View itemView) {
            super(itemView);
            btn = itemView.findViewById(R.id.btnCategory);
        }
    }
}