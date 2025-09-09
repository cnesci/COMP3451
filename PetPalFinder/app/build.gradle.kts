plugins {
    id("com.android.application")
    id("androidx.navigation.safeargs") // Java Safe Args plugin
}

android {
    namespace = "com.example.petpalfinder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.petpalfinder"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0-P4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // --- Secrets pulled from gradle.properties
        buildConfigField(
            "String",
            "OPEN_CAGE_API_KEY",
            "\"${project.findProperty("OPENCAGE_API_KEY") ?: ""}\""
        )

        val pfId = (project.findProperty("PETFINDER_CLIENT_ID") as String?) ?: ""
        val pfSecret = (project.findProperty("PETFINDER_CLIENT_SECRET") as String?) ?: ""
        buildConfigField("String", "PETFINDER_CLIENT_ID", "\"$pfId\"")
        buildConfigField("String", "PETFINDER_CLIENT_SECRET", "\"$pfSecret\"")

        // Mapbox
        val mapboxToken = (project.findProperty("MAPBOX_PUBLIC_TOKEN") as String?) ?: ""
        resValue("string", "mapbox_access_token", mapboxToken)
        manifestPlaceholders["MAPBOX_ACCESS_TOKEN"] = mapboxToken
        buildConfigField("String", "MAPBOX_PUBLIC_TOKEN", "\"$mapboxToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    // Enable BuildConfig
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.fragment)
    implementation(libs.activity)

    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    implementation(libs.glide.core)
    annotationProcessor(libs.glide.compiler)

    // ViewModel & LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.4")

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)

    implementation("androidx.viewpager2:viewpager2:1.0.0") // for the photo carousel

    // Mapbox Maps SDK
    implementation("com.mapbox.maps:android:10.18.1")
    implementation("androidx.core:core-ktx:1.13.1")
}
