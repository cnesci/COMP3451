package com.example.petpalfinder.ui.favorites;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.petpalfinder.R;
import com.example.petpalfinder.database.AppDatabase;
import com.example.petpalfinder.database.FavoriteAnimal;
import com.example.petpalfinder.model.petfinder.Animal;
import com.example.petpalfinder.model.petfinder.Photo;
import com.example.petpalfinder.ui.search.AnimalAdapter;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FavoritesFragment extends Fragment {

    private AnimalAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorites, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = view.findViewById(R.id.list_favorites);
        TextView noFavoritesText = view.findViewById(R.id.text_no_favorites);
        MaterialToolbar toolbar = view.findViewById(R.id.toolbar_favorites);

        toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

        adapter = new AnimalAdapter(animal -> {
            FavoritesFragmentDirections.ActionFavoritesFragmentToPetDetailFragment action =
                    FavoritesFragmentDirections.actionFavoritesFragmentToPetDetailFragment(animal.id);
            NavHostFragment.findNavController(this).navigate(action);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        AppDatabase db = AppDatabase.getDatabase(requireContext());
        db.favoriteDao().getAllFavorites().observe(getViewLifecycleOwner(), favoriteAnimals -> {
            if (favoriteAnimals == null || favoriteAnimals.isEmpty()) {
                noFavoritesText.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                noFavoritesText.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                adapter.setItems(convertToAnimalList(favoriteAnimals));
            }
        });
    }

    private List<Animal> convertToAnimalList(List<FavoriteAnimal> favorites) {
        List<Animal> animals = new ArrayList<>();
        for (FavoriteAnimal fav : favorites) {
            Animal animal = new Animal();
            animal.id = fav.id;
            animal.name = fav.name;
            animal.age = fav.age;
            animal.gender = fav.gender;
            animal.size = fav.size;

            if (fav.photoUrl != null) {
                Photo photo = new Photo();
                photo.medium = fav.photoUrl;
                animal.photos = Collections.singletonList(photo);
            }
            animals.add(animal);
        }
        return animals;
    }
}