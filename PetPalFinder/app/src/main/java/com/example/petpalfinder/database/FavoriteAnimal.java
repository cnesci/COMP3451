package com.example.petpalfinder.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

import com.example.petpalfinder.model.petfinder.Animal;
import com.example.petpalfinder.model.petfinder.Photo;

@Entity(tableName = "favorites")
public class FavoriteAnimal {
    @PrimaryKey
    public long id;
    public String name;
    public String photoUrl;
    public String age;
    public String gender;
    public String size;

    public FavoriteAnimal() {}

    // Helper constructor to easily convert an Animal to a FavoriteAnimal
    public FavoriteAnimal(@NonNull Animal animal) {
        this.id = animal.id;
        this.name = animal.name;
        this.age = animal.age;
        this.gender = animal.gender;
        this.size = animal.size;

        if (animal.photos != null && !animal.photos.isEmpty()) {
            Photo p = animal.photos.get(0);
            if (p.medium != null) this.photoUrl = p.medium;
            else if (p.large != null) this.photoUrl = p.large;
            else if (p.small != null) this.photoUrl = p.small;
            else this.photoUrl = p.full;
        }
    }
}