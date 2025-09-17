package com.example.petpalfinder.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FavoriteAnimal favorite);

    @Delete
    void delete(FavoriteAnimal favorite);

    @Query("SELECT * FROM favorites ORDER BY name ASC")
    LiveData<List<FavoriteAnimal>> getAllFavorites();

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :animalId LIMIT 1)")
    LiveData<Boolean> isFavorite(long animalId);
}