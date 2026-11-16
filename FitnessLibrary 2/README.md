# Fitness Exercise Library (Team 9) – Kotlin + Jetpack Compose

API: wger (https://wger.de/api/v2/)

Features
- Browse exercises (paginated, infinite scroll), filter by category, search by name
- Exercise detail: images, muscles, equipment, description
- Workout plans with CRUD (create, add/remove exercises, delete) stored in Room
- Tick exercises and "Finish workout" to log it; view/delete in Completed tab

Run: open this folder in Android Studio (Koala+), let Gradle sync, run on emulator/device with internet.
Architecture: Retrofit -> Repository -> ViewModel (StateFlow) -> Compose; Room for local data.
