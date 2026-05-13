# Firebase setup cho NovelReader

Project Android hien tai dung package `com.example.novelreader` va da co `app/google-services.json`.

## 1. Firebase Console

1. Mo project Firebase `novelreader-886a4`.
2. Authentication > Sign-in method > bat `Email/Password`.
3. Realtime Database > Create database.
4. Database Rules > dan noi dung tu `firebase/database.rules.json` roi Publish.
5. Project settings > Android app `com.example.novelreader` > tai lai `google-services.json`.
6. Dat file moi vao `D:\Android\NovelReader\app\google-services.json`.

## 2. Realtime Database nodes app dang dung

- `users`: ho so, role, ban info.
- `chat/messages`: chat cong dong, guest chi doc.
- `community/prompts`: dien dan prompt, guest doc/sao chep.
- `community/shared_novels`: metadata truyen TXT chia se, guest doc/tai.
- `community/shared_novels_content`: noi dung TXT rieng, guest doc de import.
- `community/reviews`: danh gia/review.
- `community/groups` va `community/group_messages`: nhom chat.

## 3. Ghi chu khi test

- JDK can dung la 17. Neu terminal van hien Java 8, dat `JAVA_HOME` ve `C:\Program Files\Java\jdk-17.0.19`.
- App dang tro ro Realtime Database URL trong Hilt: `https://novelreader-886a4-default-rtdb.asia-southeast1.firebasedatabase.app`.
- File `google-services.json` nen co `firebase_url` sau khi tao Realtime Database. Neu chua co, URL trong Hilt van giup app ket noi dung vung, nhung nen tai lai file config cho dong bo.
- App dang pin Firebase BoM `33.7.0` vi dependencies dang dung `firebase-*-ktx`. Neu nang BoM len 34.x thi can doi sang module Firebase khong `-ktx`.
