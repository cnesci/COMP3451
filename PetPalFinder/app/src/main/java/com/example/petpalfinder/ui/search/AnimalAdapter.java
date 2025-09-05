package com.example.petpalfinder.ui.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.petpalfinder.R;
import com.example.petpalfinder.model.petfinder.Animal;
import com.example.petpalfinder.model.petfinder.Photo;
import java.util.ArrayList;
import java.util.List;

public class AnimalAdapter extends RecyclerView.Adapter<AnimalAdapter.VH> {

    public interface OnClick { void onAnimal(Animal a); }

    private final List<Animal> data = new ArrayList<>();
    private final OnClick onClick;

    public AnimalAdapter(OnClick onClick) { this.onClick = onClick; }

    public void setItems(List<Animal> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vType) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_animal, p, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        final Animal a = data.get(pos);
        h.name.setText(a.name != null ? a.name : "(Unnamed)");
        String meta = (a.age != null ? a.age : "?") + " • " + (a.gender != null ? a.gender : "?") + " • " + (a.size != null ? a.size : "?");
        h.meta.setText(meta);
        h.distance.setText(a.distance != null ? String.format("%.1f mi", a.distance) : "");
        String img = firstPhoto(a.photos);
        Glide.with(h.img.getContext()).load(img).placeholder(R.drawable.ic_paw).into(h.img);
        h.itemView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onClick.onAnimal(a); }
        });
    }

    @Override public int getItemCount() { return data.size(); }

    private String firstPhoto(List<Photo> photos) {
        if (photos == null || photos.isEmpty()) return null;
        if (photos.get(0).medium != null) return photos.get(0).medium;
        if (photos.get(0).small != null) return photos.get(0).small;
        if (photos.get(0).large != null) return photos.get(0).large;
        return photos.get(0).full;
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView img; TextView name; TextView meta; TextView distance;
        VH(@NonNull View v) {
            super(v);
            img = v.findViewById(R.id.img);
            name = v.findViewById(R.id.name);
            meta = v.findViewById(R.id.meta);
            distance = v.findViewById(R.id.distance);
        }
    }
}
