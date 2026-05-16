# NovelReader

NovelReader là đồ án Android xây dựng ứng dụng đọc, dịch và chia sẻ truyện chữ trên điện thoại. Dự án được phát triển theo mô hình MVVM, sử dụng Jetpack Compose cho giao diện, Room cho dữ liệu cục bộ, Firebase cho tài khoản/cộng đồng và Gemini API cho chức năng dịch chương truyện.

## Mục tiêu dự án

Ứng dụng hướng tới trải nghiệm đọc truyện chữ gọn, dễ dùng và có khả năng mở rộng. Người dùng có thể nhập truyện TXT vào giá sách, đọc theo chương, tùy chỉnh giao diện đọc, dịch chương bằng API Gemini cá nhân, lưu bản dịch để tránh tốn token khi đọc lại, và tham gia các khu vực cộng đồng như chat, chia sẻ truyện TXT hoặc chia sẻ prompt dịch.

## Chức năng hiện có

### Giá sách

- Hiển thị danh sách truyện đã thêm vào máy.
- Nhập truyện từ file TXT.
- Tự tách chương theo regex mặc định hoặc regex người dùng nhập.
- Nếu không tách được chương đúng định dạng, app chia nội dung thành các phần nhỏ an toàn để tránh crash.
- Sắp xếp truyện theo lần đọc gần nhất.
- Truyện đang đọc gần nhất có banner riêng và vẫn có menu xem thông tin/xóa truyện.
- Cho phép chỉnh thông tin truyện và đổi ảnh bìa từ thiết bị.

### Trình đọc

- Đọc truyện theo chương.
- Lưu tiến độ đọc theo chương và vị trí cuộn.
- Khi chuyển chương, app giữ vị trí tương đối thay vì luôn nhảy về đầu chương.
- Tùy chỉnh cỡ chữ, font chữ, giãn dòng, nền đọc và chế độ tối.
- Hỗ trợ đánh dấu chương và đọc bằng Text-to-Speech.
- Dịch chương bằng Gemini API key của người dùng.
- Lưu cả tên chương đã dịch và nội dung đã dịch trong Room.
- Có nút chuyển Raw/Bản dịch và nút dịch lại để ghi đè bản dịch lỗi.
- Khi chương đã có bản dịch, app ưu tiên mở lại bản dịch.
- Chương dài được chia nhỏ khi gửi Gemini để giảm lỗi vượt giới hạn token.

### Khám phá

- Có giao diện danh sách truyện, tìm kiếm và màn hình chi tiết truyện.
- Có luồng thêm truyện từ màn chi tiết vào giá sách.
- Phần crawl truyện từ website đang được để lại để tiếp tục hoàn thiện ở giai đoạn sau.

### Cộng đồng

- Chat cộng đồng dùng Firebase Realtime Database.
- Người dùng chưa đăng nhập có thể xem nội dung công khai nhưng không thể gửi chat, đăng bài hoặc bình luận.
- Người dùng đăng nhập có thể:
  - Gửi tin nhắn cộng đồng.
  - Chia sẻ truyện TXT để người khác tải và nhập vào giá sách.
  - Chia sẻ prompt dịch.
  - Bình luận dưới bài chia sẻ truyện và prompt.
- Bình luận không hiển thị tràn bên ngoài card; khi bấm vào bài đăng sẽ mở trang bình luận dạng hội thoại.
- Dữ liệu chia sẻ dùng Realtime Database, không phụ thuộc Firebase Storage để phù hợp project miễn phí.

### Cá nhân

- Đăng ký, đăng nhập, đăng xuất bằng Firebase Authentication.
- Cập nhật tên và ảnh đại diện.
- Ảnh đại diện được lưu theo dạng dữ liệu có thể hiển thị cho người dùng khác mà không cần Firebase Storage.
- Lưu Gemini API key và prompt dịch tùy chỉnh.
- Cài đặt đọc: chế độ tối, font chữ, cỡ chữ, giãn dòng, nền đọc và tự động mở truyện gần nhất.
- Có phân quyền cơ bản cho user, mod và admin.

## Kiến trúc

Dự án đang tổ chức theo hướng MVVM và chia lớp rõ ràng:

```text
app/src/main/java/com/example/novelreader
├── data
│   ├── local        # Room database, DAO, entity
│   ├── remote       # Firebase, Gemini API, crawler, upload helper
│   └── repository   # Repository implementation
├── di               # Hilt modules
├── domain
│   ├── model        # Model thuần Kotlin
│   └── repository   # Interface repository
└── presentation
    ├── ui           # Jetpack Compose screens
    └── viewmodel    # ViewModel và UI state
```

Luồng chính:

```text
UI Compose -> ViewModel -> Repository interface -> Repository implementation -> Room/Firebase/API
```

## Công nghệ sử dụng

- Kotlin 2.0.21
- Android Gradle Plugin 8.7.3
- Jetpack Compose + Material 3
- Navigation Compose
- Hilt
- Room
- DataStore Preferences
- Firebase Authentication
- Firebase Realtime Database
- Firebase Firestore dependency đã cấu hình, nhưng luồng cộng đồng chính hiện dùng Realtime Database
- Coil
- OkHttp/Gson cho Gemini API
- Jsoup cho phần crawler sẽ hoàn thiện sau
- Text-to-Speech Android

## Yêu cầu môi trường

- Android Studio phiên bản mới
- JDK 17
- Android SDK với compileSdk 35
- Thiết bị hoặc emulator Android API 26 trở lên
- Firebase project có Authentication và Realtime Database

Nếu terminal chưa nhận JDK 17, có thể đặt:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17.0.19'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

## Cấu hình Firebase

1. Tạo hoặc mở Firebase project.
2. Bật Authentication bằng Email/Password.
3. Tạo Realtime Database.
4. Dán rules trong `firebase/database.rules.json`.
5. Tải `google-services.json` của Android app package `com.example.novelreader`.
6. Đặt file tại:

```text
app/google-services.json
```

Chi tiết bổ sung nằm trong `docs/firebase-setup.md`.

## Chạy dự án

Build debug:

```powershell
.\gradlew.bat :app:assembleDebug
```

Cài APK debug lên emulator:

```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Mở app:

```powershell
& $adb shell am start -n com.example.novelreader/.MainActivity
```

## Trạng thái hiện tại

Đã hoàn thiện phần lõi cho đọc truyện TXT, lưu tiến độ, dịch bằng Gemini, lưu bản dịch, cộng đồng cơ bản, tài khoản, cài đặt cá nhân và giao diện chính 4 tab. Phần crawl truyện từ website đã có nền tảng nhưng chưa phải trọng tâm hoàn thiện ở giai đoạn hiện tại.

## Hướng phát triển tiếp theo

- Hoàn thiện crawler theo từng nguồn truyện cụ thể.
- Bổ sung kiểm thử tự động cho import TXT, tách chương, lưu tiến độ và dịch chương.
- Cải thiện quản lý token và hàng đợi dịch nhiều chương.
- Bổ sung đồng bộ dữ liệu đọc giữa nhiều thiết bị.
- Tối ưu giao diện tablet và màn hình ngang.
- Hoàn thiện moderation cộng đồng cho mod/admin.
