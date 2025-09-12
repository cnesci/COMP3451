package com.example.petpalfinder.ui.search;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.petpalfinder.databinding.SheetFiltersBinding;
import com.example.petpalfinder.data.FilterParams;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FilterBottomSheetFragment extends BottomSheetDialogFragment {

    public interface Listener {
        void onFiltersApplied(FilterParams params);
    }

    private SheetFiltersBinding binding;
    private Listener listener;
    private FilterParams current;

    public static FilterBottomSheetFragment newInstance(FilterParams current) {
        FilterBottomSheetFragment f = new FilterBottomSheetFragment();
        Bundle b = new Bundle();
        b.putString("type", current.type);
        // multi-select
        b.putStringArrayList("types", new ArrayList<>(current.types));

        b.putStringArrayList("genders", new ArrayList<>(current.genders));
        b.putStringArrayList("ages",    new ArrayList<>(current.ages));
        b.putStringArrayList("sizes",   new ArrayList<>(current.sizes));

        b.putBoolean("gwk", current.goodWithChildren);
        b.putBoolean("gwd", current.goodWithDogs);
        b.putBoolean("gwc", current.goodWithCats);
        b.putInt("distKm", current.distanceKm == null ? 50 : current.distanceKm);
        f.setArguments(b);
        return f;
    }

    @Override public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (getParentFragment() instanceof Listener) listener = (Listener) getParentFragment();
        else if (context instanceof Listener)       listener = (Listener) context;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetFiltersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        current = new FilterParams();
        Bundle args = getArguments() == null ? new Bundle() : getArguments();

        current.type  = args.getString("type", null); // legacy single
        current.types = new ArrayList<>(safeList(args.getStringArrayList("types")));

        current.genders = new ArrayList<>(safeList(args.getStringArrayList("genders")));
        current.ages    = new ArrayList<>(safeList(args.getStringArrayList("ages")));
        current.sizes   = new ArrayList<>(safeList(args.getStringArrayList("sizes")));

        current.goodWithChildren = args.getBoolean("gwk", false);
        current.goodWithDogs = args.getBoolean("gwd", false);
        current.goodWithCats = args.getBoolean("gwc", false);
        current.distanceKm   = args.getInt("distKm", 50);
        current.sort = "distance";

        preselectMulti(binding.chipGroupType,   current.types);
        preselectMulti(binding.chipGroupGender, current.genders);
        preselectMulti(binding.chipGroupAge,    current.ages);
        preselectMulti(binding.chipGroupSize,   current.sizes);

        binding.switchKids.setChecked(current.goodWithChildren);
        binding.switchDogs.setChecked(current.goodWithDogs);
        binding.switchCats.setChecked(current.goodWithCats);
        binding.sliderDistance.setValue(current.distanceKm);

        binding.btnClear.setOnClickListener(v1 -> {
            clearGroup(binding.chipGroupType);
            clearGroup(binding.chipGroupGender);
            clearGroup(binding.chipGroupAge);
            clearGroup(binding.chipGroupSize);
            binding.switchKids.setChecked(false);
            binding.switchDogs.setChecked(false);
            binding.switchCats.setChecked(false);
            binding.sliderDistance.setValue(50);
        });

        binding.btnApply.setOnClickListener(v12 -> {
            current.types   = selectedTags(binding.chipGroupType);
            current.genders = selectedTags(binding.chipGroupGender);
            current.ages    = selectedTags(binding.chipGroupAge);
            current.sizes   = selectedTags(binding.chipGroupSize);

            current.goodWithChildren = binding.switchKids.isChecked();
            current.goodWithDogs = binding.switchDogs.isChecked();
            current.goodWithCats = binding.switchCats.isChecked();
            current.distanceKm   = (int) binding.sliderDistance.getValue();

            if (listener != null) listener.onFiltersApplied(current);
            dismiss();
        });
    }

    private static List<String> safeList(@Nullable List<String> in) {
        return in == null ? new ArrayList<>() : in;
    }

    private static void clearGroup(ChipGroup cg) {
        for (int i = 0; i < cg.getChildCount(); i++) {
            View c = cg.getChildAt(i);
            if (c instanceof Chip) ((Chip) c).setChecked(false);
        }
    }

    private static void preselectMulti(ViewGroup chipGroup, List<String> keys) {
        if (keys == null || keys.isEmpty()) return;
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            View c = chipGroup.getChildAt(i);
            if (c instanceof Chip) {
                Object tag = c.getTag();
                if (tag != null && keys.contains(String.valueOf(tag))) {
                    ((Chip) c).setChecked(true);
                }
            }
        }
    }

    private static ArrayList<String> selectedTags(ViewGroup chipGroup) {
        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            View c = chipGroup.getChildAt(i);
            if (c instanceof Chip && ((Chip) c).isChecked()) {
                Object tag = c.getTag();
                if (tag != null) out.add(String.valueOf(tag));
            }
        }
        return out;
    }
}
