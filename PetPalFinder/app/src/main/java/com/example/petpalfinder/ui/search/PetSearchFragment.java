package com.example.petpalfinder.ui.search;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.petpalfinder.R;
import com.example.petpalfinder.data.FilterParams;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.Map;

public class PetSearchFragment extends Fragment implements FilterBottomSheetFragment.Listener {

    private PetSearchViewModel vm;
    private AnimalAdapter adapter;
    private RecyclerView list;
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_pet_search, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        vm = new ViewModelProvider(requireActivity()).get(PetSearchViewModel.class);
        prefs = requireContext().getSharedPreferences("filters", Context.MODE_PRIVATE);

        list = v.findViewById(R.id.list);
        ProgressBar pb = v.findViewById(R.id.progress);
        MaterialToolbar toolbar = v.findViewById(R.id.toolbar);

        if (toolbar != null) {
            toolbar.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_toggle_map) {
                    // Keep passing through the original args for location only.
                    Bundle argsBundle = (getArguments() != null) ? getArguments() : new Bundle();
                    PetSearchFragmentArgs args = PetSearchFragmentArgs.fromBundle(argsBundle);
                    Bundle b = new Bundle();
                    b.putString("location", args.getLocation());
                    NavHostFragment.findNavController(PetSearchFragment.this)
                            .navigate(R.id.mapFragment, b);
                    return true;
                } else if (id == R.id.action_filters) {
                    FilterParams cur = vm.getFilters().getValue();
                    if (cur == null) {
                        cur = FilterParams.fromPrefs(prefs.getAll(), /*typeArg=*/null);
                    }
                    FilterBottomSheetFragment
                            .newInstance(cur)
                            .show(getChildFragmentManager(), "filters");
                    return true;
                }
                return false;
            });

            toolbar.setNavigationOnClickListener(click ->
                    NavHostFragment.findNavController(PetSearchFragment.this).navigateUp());
        }

        adapter = new AnimalAdapter(a -> {
            PetSearchFragmentDirections.ActionPetSearchFragmentToPetDetailFragment dir =
                    PetSearchFragmentDirections.actionPetSearchFragmentToPetDetailFragment(a.id);
            NavHostFragment.findNavController(PetSearchFragment.this).navigate(dir);
        });

        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);

        vm.loading().observe(getViewLifecycleOwner(),
                isLoading -> pb.setVisibility(Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE));

        vm.error().observe(getViewLifecycleOwner(), err -> {
            if (!TextUtils.isEmpty(err)) {
                Toast.makeText(getContext(), err, Toast.LENGTH_LONG).show();
            }
        });

        vm.results().observe(getViewLifecycleOwner(), adapter::setItems);

        // ----- Initialization: set location, then apply saved filters (no type seeding) -----
        Bundle argsBundle = (getArguments() != null) ? getArguments() : new Bundle();
        PetSearchFragmentArgs args = PetSearchFragmentArgs.fromBundle(argsBundle);
        String location = args.getLocation();
        if (location == null || location.isEmpty()) {
            location = "43.6532,-79.3832";
        }

        vm.firstSearch(/*type=*/null, location);

        FilterParams saved = FilterParams.fromPrefs(prefs.getAll(), /*typeArg=*/null);
        vm.applyFilters(saved);

        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int visible = lm.getChildCount();
                int total = lm.getItemCount();
                int first = lm.findFirstVisibleItemPosition();
                if (first + visible >= (int) (0.8 * total)) vm.nextPage();
            }
        });
    }

    @Override
    public void onFiltersApplied(FilterParams params) {
        vm.applyFilters(params);
        SharedPreferences.Editor e = prefs.edit();
        for (Map.Entry<String, String> en : params.toPrefs().entrySet()) {
            e.putString(en.getKey(), en.getValue());
        }
        e.apply();
        if (list != null) list.scrollToPosition(0);
    }
}
