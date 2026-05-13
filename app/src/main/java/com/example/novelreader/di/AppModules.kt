package com.example.novelreader.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.novelreader.data.local.AppDatabase
import com.example.novelreader.data.local.dao.*
import com.example.novelreader.data.remote.api.GeminiTranslationService
import com.example.novelreader.data.remote.crawler.NovelCrawlerService
import com.example.novelreader.data.remote.firebase.*
import com.example.novelreader.data.remote.upload.ExternalFileUploadService
import com.example.novelreader.data.remote.upload.TransferShUploadService
import com.example.novelreader.data.repository.*
import com.example.novelreader.domain.repository.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.FirebaseDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// ============================================================
// HILT DI MODULES — Full setup
// ============================================================

private val Context.dataStore: DataStore<Preferences>
    by preferencesDataStore(name = "reader_settings")

// ─────────────────────────────────────────────────────────────
// ROOM DATABASE
// ─────────────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideBookDao(db: AppDatabase): BookDao = db.bookDao()
    @Provides fun provideChapterDao(db: AppDatabase): ChapterDao = db.chapterDao()
    @Provides fun provideHistoryDao(db: AppDatabase): ReadingHistoryDao = db.readingHistoryDao()
    @Provides fun providePromptDao(db: AppDatabase): PromptDao = db.promptDao()
    @Provides fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore
}

// ─────────────────────────────────────────────────────────────
// FIREBASE
// ─────────────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    private const val DATABASE_URL =
        "https://novelreader-886a4-default-rtdb.asia-southeast1.firebasedatabase.app"

    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase =
        FirebaseDatabase.getInstance(DATABASE_URL).also {
            try {
                it.setPersistenceEnabled(true)   // Disk cache for offline support
            } catch (_: DatabaseException) {
                // Persistence can only be enabled before the first DB use in a process.
            }
        }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    // ── Concrete Firebase repository objects ──────────────────
    // Provided as objects (not just bound) so they can also be
    // injected directly by classes that need the concrete type
    // (e.g. FirebaseChatRepository needs FirebaseUserRepository).

    @Provides
    @Singleton
    fun provideFirebaseUserRepository(
        database: FirebaseDatabase,
        auth: FirebaseAuth
    ): FirebaseUserRepository = FirebaseUserRepository(database, auth)

    @Provides
    @Singleton
    fun provideFirebaseChatRepository(
        database: FirebaseDatabase,
        auth: FirebaseAuth,
        userRepo: FirebaseUserRepository
    ): FirebaseChatRepository = FirebaseChatRepository(database, auth, userRepo)

    @Provides
    @Singleton
    fun provideFirebasePromptRepository(
        database: FirebaseDatabase,
        auth: FirebaseAuth,
        userRepo: FirebaseUserRepository
    ): FirebasePromptRepository = FirebasePromptRepository(database, auth, userRepo)

    @Provides
    @Singleton
    fun provideFirebaseSharedBookRepository(
        database: FirebaseDatabase,
        auth: FirebaseAuth,
        userRepo: FirebaseUserRepository
    ): FirebaseSharedBookRepository = FirebaseSharedBookRepository(database, auth, userRepo)
}

// ─────────────────────────────────────────────────────────────
// UPLOAD SERVICE
// ─────────────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
object UploadModule {

    /**
     * Swap TransferShUploadService for FileIoUploadService or
     * ManualUrlUploadService here without touching any other code.
     */
    @Provides
    @Singleton
    fun provideExternalFileUploadService(): ExternalFileUploadService =
        TransferShUploadService()
}

// ─────────────────────────────────────────────────────────────
// REPOSITORY BINDINGS — interface → implementation
// ─────────────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    // ── Room-backed (local) ───────────────────────────────────

    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    @Singleton
    abstract fun bindChapterRepository(impl: ChapterRepositoryImpl): ChapterRepository

    // ── Firebase-backed ───────────────────────────────────────

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: FirebaseUserRepository): UserRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: FirebaseChatRepository): ChatRepository

    @Binds
    @Singleton
    abstract fun bindPromptRepository(impl: FirebasePromptRepository): PromptRepository

    @Binds
    @Singleton
    abstract fun bindSharedBookRepository(impl: FirebaseSharedBookRepository): SharedBookRepository
}
