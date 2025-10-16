package com.example.petpalfinder.ui.search;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Layout;
import android.text.TextUtils;
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
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.example.petpalfinder.R;
import com.example.petpalfinder.model.petfinder.Animal;
import com.example.petpalfinder.model.petfinder.Photo;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PetDetailFragment extends Fragment {

    private PetDetailViewModel vm;
    private PhotoPagerAdapter pagerAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_pet_detail, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        vm = new ViewModelProvider(this).get(PetDetailViewModel.class);

        FloatingActionButton fabFavorite = v.findViewById(R.id.fab_favorite);

        // Carousel and arrows
        ViewPager2 pager = v.findViewById(R.id.photoPager);
        TabLayout dots = v.findViewById(R.id.photoDots);
        ImageView arrowLeft = v.findViewById(R.id.arrow_left);
        ImageView arrowRight = v.findViewById(R.id.arrow_right);
        pagerAdapter = new PhotoPagerAdapter();
        pager.setAdapter(pagerAdapter);
        pager.setOffscreenPageLimit(1);
        new TabLayoutMediator(dots, pager, (tab, position) -> {}).attach();

        // UI refs
        TextView name = v.findViewById(R.id.name);
        TextView meta = v.findViewById(R.id.meta);
        TextView distance = v.findViewById(R.id.distance);
        TextView desc = v.findViewById(R.id.description);
        Button openWeb = v.findViewById(R.id.btnViewOnPetfinder);
        Button email = v.findViewById(R.id.btnEmail);
        Button phone = v.findViewById(R.id.btnPhone);
        ProgressBar pb = v.findViewById(R.id.progress);

        vm.loading().observe(getViewLifecycleOwner(),
                isLoading -> pb.setVisibility(Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE));

        vm.error().observe(getViewLifecycleOwner(), err -> {
            if (!TextUtils.isEmpty(err)) Toast.makeText(getContext(), err, Toast.LENGTH_LONG).show();
        });

        vm.animal().observe(getViewLifecycleOwner(), a -> {
            if (a == null) return;

            // --------- CAROUSEL & ARROWS ---------
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
            if (urls.isEmpty()) urls.add(null); // placeholder slide
            pagerAdapter.setPhotos(urls);

            // Arrow visibility logic
            pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    arrowLeft.setVisibility(position > 0 ? View.VISIBLE : View.GONE);
                    arrowRight.setVisibility(position < urls.size() - 1 ? View.VISIBLE : View.GONE);
                }
            });

            // Initial state
            arrowLeft.setVisibility(View.GONE);
            arrowRight.setVisibility(urls.size() > 1 ? View.VISIBLE : View.GONE);

            // Arrow click listeners
            arrowLeft.setOnClickListener(view -> pager.setCurrentItem(pager.getCurrentItem() - 1, true));
            arrowRight.setOnClickListener(view -> pager.setCurrentItem(pager.getCurrentItem() + 1, true));

            // ... (rest of the observer code remains the same)

            // --------- HEADER TEXT ---------
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

            if (openWeb != null) {
                openWeb.setOnClickListener(vv -> {
                    if (!TextUtils.isEmpty(a.url)) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(a.url)));
                    } else {
                        Toast.makeText(getContext(), "Listing URL not available for this pet.", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // --------- DESCRIPTION: HTML base + composed “About” (bulleted) ---------
            CharSequence about = composeDescription(a);
            desc.setText(TextUtils.isEmpty(about) ? "No description available." : about);

            // Never truncate
            desc.setSingleLine(false);
            desc.setHorizontallyScrolling(false);
            desc.setEllipsize(null);
            desc.setMaxLines(Integer.MAX_VALUE);
            try {
                desc.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY);
                desc.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
            } catch (Throwable ignore) {}

            desc.post(() -> {
                desc.setSingleLine(false);
                desc.setHorizontallyScrolling(false);
                desc.setEllipsize(null);
                desc.setMaxLines(Integer.MAX_VALUE);
                try {
                    desc.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY);
                    desc.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
                } catch (Throwable ignore) {}
            });

            // --------- CONTACT BUTTONS ---------
            String rawEmail = (a.contact != null) ? a.contact.email : null;
            String rawPhone = (a.contact != null) ? a.contact.phone : null;
            String emailAddr = normalizeEmail(rawEmail);
            String phoneNum  = normalizePhone(rawPhone);

            email.setEnabled(!TextUtils.isEmpty(emailAddr));
            phone.setEnabled(!TextUtils.isEmpty(phoneNum));

            email.setOnClickListener(v1 -> {
                if (TextUtils.isEmpty(emailAddr)) {
                    Toast.makeText(getContext(), "No email provided by the shelter.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setData(Uri.parse("mailto:"));
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{ emailAddr });
                intent.putExtra(Intent.EXTRA_SUBJECT, "Pet adoption inquiry: " + (a.name != null ? a.name : "Pet"));
                if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                    startActivity(Intent.createChooser(intent, "Send email"));
                } else {
                    Intent viewIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("mailto:" + Uri.encode(emailAddr)));
                    if (viewIntent.resolveActivity(requireContext().getPackageManager()) != null) {
                        startActivity(viewIntent);
                    } else {
                        Toast.makeText(getContext(), "No email app found on this device.", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            phone.setOnClickListener(v12 -> {
                if (TextUtils.isEmpty(phoneNum)) {
                    Toast.makeText(getContext(), "No phone number provided by the shelter.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phoneNum)));
                if (dial.resolveActivity(requireContext().getPackageManager()) != null) {
                    startActivity(dial);
                } else {
                    Toast.makeText(getContext(), "No dialer app found on this device.", Toast.LENGTH_SHORT).show();
                }
            });
        });

        long id = PetDetailFragmentArgs.fromBundle(getArguments()).getAnimalId();

        vm.isFavorite(id).observe(getViewLifecycleOwner(), isFavorited -> {
            if (isFavorited) {
                fabFavorite.setImageResource(R.drawable.ic_favorite_24);
            } else {
                fabFavorite.setImageResource(R.drawable.ic_favorite_border_24);
            }
        });

        fabFavorite.setOnClickListener(view -> vm.toggleFavorite());

        vm.load(id);
    }

    private String safe(String s) { return s == null ? "?" : s; }

    private CharSequence composeDescription(Animal a) {
        StringBuilder sb = new StringBuilder();

        if (!TextUtils.isEmpty(a.description)) {
            CharSequence sp = HtmlCompat.fromHtml(a.description, HtmlCompat.FROM_HTML_MODE_LEGACY);
            sb.append(stripTrailingWhitespace(sp.toString())).append("\n\n");
        }

        String breed = joinPair(a.breeds != null ? a.breeds.primary : null,
                a.breeds != null ? a.breeds.secondary : null, " & ");
        String color = joinPair(a.colors != null ? a.colors.primary : null,
                a.colors != null ? a.colors.secondary : null, ", ");
        List<String> parts = new ArrayList<>();
        addIfNotEmpty(parts, a.age);
        addIfNotEmpty(parts, a.gender);
        addIfNotEmpty(parts, a.size);
        if (!TextUtils.isEmpty(breed)) parts.add(breed);
        if (!TextUtils.isEmpty(a.coat)) parts.add(a.coat + " coat");
        if (!TextUtils.isEmpty(color)) parts.add("color: " + color);
        String facts = TextUtils.join(", ", parts);
        if (!TextUtils.isEmpty(facts)) sb.append("• ").append(facts).append("\n");

        String health = bullets(
                flag(a.attributes != null ? a.attributes.spayed_neutered : null, "Spayed/Neutered"),
                flag(a.attributes != null ? a.attributes.shots_current   : null, "Vaccinations up to date"),
                flag(a.attributes != null ? a.attributes.house_trained   : null, "House-trained"),
                flag(a.attributes != null ? a.attributes.declawed        : null, "Declawed"),
                flag(a.attributes != null ? a.attributes.special_needs   : null, "Special needs")
        );
        if (!TextUtils.isEmpty(health)) sb.append(health);

        String env = bullets(
                boolLine(a.environment != null ? a.environment.children : null, "Good with children"),
                boolLine(a.environment != null ? a.environment.dogs     : null, "Good with dogs"),
                boolLine(a.environment != null ? a.environment.cats     : null, "Good with cats")
        );
        if (!TextUtils.isEmpty(env)) sb.append(env);

        if (a.tags != null && !a.tags.isEmpty()) {
            sb.append("• Personality: ").append(TextUtils.join(", ", a.tags)).append("\n");
        }

        String locality = null;
        if (a.contact != null && a.contact.address != null) {
            locality = joinPair(a.contact.address.city, a.contact.address.state, ", ");
            if (TextUtils.isEmpty(locality)) locality = a.contact.address.postcode;
        }
        if (!TextUtils.isEmpty(locality)) sb.append("• Location: ").append(locality).append("\n");

        return stripTrailingWhitespace(sb.toString());
    }

    private void addIfNotEmpty(List<String> list, String value) {
        if (!TextUtils.isEmpty(value)) list.add(value);
    }

    private String joinPair(String a, String b, String sep) {
        if (TextUtils.isEmpty(a)) return TextUtils.isEmpty(b) ? "" : b;
        if (TextUtils.isEmpty(b)) return a;
        return a + sep + b;
    }

    private String flag(Boolean val, String label) {
        if (val == null) return null;
        return val ? label : ("Not " + label.toLowerCase());
    }

    private String boolLine(Boolean val, String label) {
        if (val == null) return null;
        return (val ? label : ("Not " + label.toLowerCase()));
    }

    private String bullets(String... lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (!TextUtils.isEmpty(line)) sb.append("• ").append(line).append("\n");
        }
        return sb.toString();
    }

    private String stripTrailingWhitespace(String s) {
        if (s == null) return "";
        int i = s.length() - 1;
        while (i >= 0 && Character.isWhitespace(s.charAt(i))) i--;
        return s.substring(0, i + 1);
    }

    private String normalizeEmail(String in) {
        if (in == null) return null;
        String s = in.trim();
        if (s.startsWith("mailto:")) s = s.substring("mailto:".length());
        return (s.contains("@") && !s.contains(" ")) ? s : null;
    }

    private String normalizePhone(String in) {
        if (in == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : in.toCharArray()) {
            if (Character.isDigit(c) || c == '+') sb.append(c);
        }
        String s = sb.toString();
        return s.length() >= 7 ? s : null;
    }
}
