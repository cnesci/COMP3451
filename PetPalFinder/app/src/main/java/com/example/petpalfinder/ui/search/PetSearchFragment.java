package com.example.petpalfinder.ui.search;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.petpalfinder.R;
import com.example.petpalfinder.model.petfinder.Animal;

public class PetSearchFragment extends Fragment {

    private PetSearchViewModel vm;
    private AnimalAdapter adapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_pet_search, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        vm = new ViewModelProvider(this).get(PetSearchViewModel.class);

        RecyclerView list = v.findViewById(R.id.list);
        ProgressBar pb = v.findViewById(R.id.progress);

        adapter = new AnimalAdapter(new AnimalAdapter.OnClick() {
            @Override public void onAnimal(Animal a) {
                Toast.makeText(getContext(), "Clicked " + a.name + " (#" + a.id + ")", Toast.LENGTH_SHORT).show();
            }
        });

        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);

        vm.loading().observe(getViewLifecycleOwner(), isLoading ->
                pb.setVisibility(Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE));

        vm.error().observe(getViewLifecycleOwner(), err -> {
            if (!TextUtils.isEmpty(err)) Toast.makeText(getContext(), err, Toast.LENGTH_LONG).show();
        });

        vm.results().observe(getViewLifecycleOwner(), adapter::setItems);

        // Toronto sample — swap with your geocoder output "lat,lon"
        vm.firstSearch("dog", "43.6532,-79.3832");

        // Simple pagination trigger (optional)
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int visible = lm.getChildCount();
                int total = lm.getItemCount();
                int first = lm.findFirstVisibleItemPosition();
                if (first + visible >= (int)(0.8 * total)) vm.nextPage();
            }
        });
    }
}
