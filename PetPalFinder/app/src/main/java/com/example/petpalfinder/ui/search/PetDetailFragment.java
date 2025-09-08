package com.example.petpalfinder.ui.search;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.petpalfinder.R;
import com.example.petpalfinder.model.petfinder.Animal;
import com.example.petpalfinder.model.petfinder.Photo;

import java.util.Locale;

public class PetDetailFragment extends Fragment {

    private PetDetailViewModel vm;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_pet_detail, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        vm = new ViewModelProvider(this).get(PetDetailViewModel.class);

        ImageView img = v.findViewById(R.id.img);
        TextView name = v.findViewById(R.id.name);
        TextView meta = v.findViewById(R.id.meta);
        TextView distance = v.findViewById(R.id.distance);
        TextView desc = v.findViewById(R.id.description);
        desc.setMovementMethod(new ScrollingMovementMethod());

        Button email = v.findViewById(R.id.btnEmail);
        Button phone = v.findViewById(R.id.btnPhone);
        ProgressBar pb = v.findViewById(R.id.progress);

        vm.loading().observe(getViewLifecycleOwner(), isLoading ->
                pb.setVisibility(Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE));

        vm.error().observe(getViewLifecycleOwner(), err -> {
            if (!TextUtils.isEmpty(err)) Toast.makeText(getContext(), err, Toast.LENGTH_LONG).show();
        });

        vm.animal().observe(getViewLifecycleOwner(), a -> {
            if (a == null) return;
            name.setText(!TextUtils.isEmpty(a.name) ? a.name : "(Unnamed)");
            String metaText = String.format(Locale.US, "%s • %s • %s",
                    safe(a.age), safe(a.gender), safe(a.size));
            meta.setText(metaText);

            if (a.distance != null) {
                // Convert miles to km for Canadian users
                double km = a.distance * 1.60934;
                distance.setText(String.format(Locale.US, "%.1f km away", km));
            } else {
                distance.setText("");
            }

            String imgUrl = firstPhoto(a);
            Glide.with(img.getContext()).load(imgUrl).placeholder(R.drawable.ic_paw).into(img);

            desc.setText(!TextUtils.isEmpty(a.description) ? a.description : "No description available.");

            // Contact buttons
            String emailAddr = (a.contact != null) ? a.contact.email : null;
            String phoneNum = (a.contact != null) ? a.contact.phone : null;

            email.setEnabled(!TextUtils.isEmpty(emailAddr));
            phone.setEnabled(!TextUtils.isEmpty(phoneNum));

            email.setOnClickListener(v1 -> {
                if (TextUtils.isEmpty(emailAddr)) return;
                Intent i = new Intent(Intent.ACTION_SENDTO);
                i.setData(Uri.parse("mailto:" + emailAddr));
                i.putExtra(Intent.EXTRA_SUBJECT, "Pet adoption inquiry: " + a.name);
                startActivity(Intent.createChooser(i, "Send email"));
            });

            phone.setOnClickListener(v12 -> {
                if (TextUtils.isEmpty(phoneNum)) return;
                Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phoneNum));
                startActivity(i);
            });
        });

        long id = PetDetailFragmentArgs.fromBundle(getArguments()).getAnimalId();
        vm.load(id);
    }

    private String firstPhoto(Animal a) {
        if (a == null || a.photos == null || a.photos.isEmpty()) return null;
        Photo p = a.photos.get(0);
        if (p.medium != null) return p.medium;
        if (p.small != null) return p.small;
        if (p.large != null) return p.large;
        return p.full;
    }

    private String safe(String s) { return s == null ? "?" : s; }
}
