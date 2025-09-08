package com.example.petpalfinder.ui.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.petpalfinder.R;

import java.util.ArrayList;
import java.util.List;

public class PhotoPagerAdapter extends RecyclerView.Adapter<PhotoPagerAdapter.VH> {

    private final List<String> urls = new ArrayList<>();

    public void setPhotos(List<String> newUrls) {
        urls.clear();
        if (newUrls != null) urls.addAll(newUrls);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_photo_pager, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        String url = urls.get(position);
        Glide.with(holder.img.getContext())
                .load(url)
                .placeholder(R.drawable.ic_paw)
                .centerInside()
                .into(holder.img);
    }

    @Override
    public int getItemCount() { return urls.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView img;
        VH(@NonNull View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.pagerImage);
        }
    }
}
