package com.example.petpalfinder.ui.search;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Layout;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.example.petpalfinder.R;
import com.example.petpalfinder.model.petfinder.Animal;
import com.example.petpalfinder.model.petfinder.Photo;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PetDetailFragment extends Fragment {

    private PetDetailViewModel vm;
    private PhotoPagerAdapter pagerAdapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_pet_detail, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        vm = new ViewModelProvider(this).get(PetDetailViewModel.class);

        // Carousel
        ViewPager2 pager = v.findViewById(R.id.photoPager);
        TabLayout dots = v.findViewById(R.id.photoDots);
        pagerAdapter = new PhotoPagerAdapter();
        pager.setAdapter(pagerAdapter);
        pager.setOffscreenPageLimit(1);
        new TabLayoutMediator(dots, pager, (tab, position) -> {}).attach();

        TextView name = v.findViewById(R.id.name);
        TextView meta = v.findViewById(R.id.meta);
        TextView distance = v.findViewById(R.id.distance);
        TextView desc = v.findViewById(R.id.description);
        Button openWeb = v.findViewById(R.id.btnViewOnPetfinder);
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

            // ---------- CAROUSEL: build photo URLs and set adapter ----------
            List<String> urls = new ArrayList<>();
            if (a.photos != null) {
                for (Photo p : a.photos) {
                    if (p == null) continue;
                    if (p.full != null)   { urls.add(p.full);   continue; }
                    if (p.large != null)  { urls.add(p.large);  continue; }
                    if (p.medium != null) { urls.add(p.medium); continue; }
                    if (p.small != null)  { urls.add(p.small);  continue; }
                }
            }
            if (urls.isEmpty()) urls.add(null);        // will show placeholder
            pagerAdapter.setPhotos(urls);              // <-- THIS WAS MISSING

            // ---------- TOP TEXT ----------
            name.setText(!TextUtils.isEmpty(a.name) ? a.name : "(Unnamed)");
            String metaText = String.format(Locale.US, "%s • %s • %s",
                    safe(a.age), safe(a.gender), safe(a.size));
            meta.setText(metaText);

            if (a.distance != null) {
                double km = a.distance * 1.60934;
                distance.setText(String.format(Locale.US, "%.1f km away", km));
            } else {
                distance.setText("");
            }

            openWeb.setOnClickListener(vv -> {
                String url = null;
                try {
                    java.lang.reflect.Field f = a.getClass().getField("url");
                    Object val = f.get(a);
                    if (val instanceof String) url = (String) val;
                } catch (Throwable ignored) {}

                if (TextUtils.isEmpty(url)) {
                    Toast.makeText(getContext(), "Listing URL not available for this pet.", Toast.LENGTH_SHORT).show();
                } else {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                }
            });


            // ---------- DESCRIPTION: HTML + never truncate ----------
            if (!TextUtils.isEmpty(a.description)) {
                Spanned sp = HtmlCompat.fromHtml(a.description, HtmlCompat.FROM_HTML_MODE_LEGACY);
                desc.setText(sp, TextView.BufferType.SPANNABLE);
                android.util.Log.d("PF_DETAIL_API",
                        "apiDescLen=" + (a.description == null ? 0 : a.description.length()));
            } else {
                desc.setText("No description available.");
            }

            // make sure no truncation is applied
            desc.setSingleLine(false);
            desc.setHorizontallyScrolling(false);
            desc.setEllipsize(null);
            desc.setMaxLines(Integer.MAX_VALUE);

            // better wrapping
            try {
                desc.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY);
                desc.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
            } catch (Throwable ignored) { }

            // Re-assert after layout (wins if a style/theme applies limits later)
            desc.post(() -> {
                desc.setSingleLine(false);
                desc.setHorizontallyScrolling(false);
                desc.setEllipsize(null);
                desc.setMaxLines(Integer.MAX_VALUE);
                try {
                    desc.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY);
                    desc.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
                } catch (Throwable ignored) { }

                Layout layout = desc.getLayout();
                boolean ellipsized = false;
                if (layout != null) {
                    for (int i = 0; i < layout.getLineCount(); i++) {
                        if (layout.getEllipsisCount(i) > 0) { ellipsized = true; break; }
                    }
                }
                android.util.Log.d("PF_DETAIL", "layoutEllipsized=" + ellipsized);
            });

            // ---------- CONTACT BUTTONS ----------
            String emailAddr = (a.contact != null) ? a.contact.email : null;
            String phoneNum = (a.contact != null) ? a.contact.phone : null;

            email.setEnabled(!TextUtils.isEmpty(emailAddr));
            phone.setEnabled(!TextUtils.isEmpty(phoneNum));

            email.setOnClickListener(v1 -> {
                if (TextUtils.isEmpty(emailAddr)) return;
                Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + emailAddr));
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

    private String safe(String s) { return s == null ? "?" : s; }
}
