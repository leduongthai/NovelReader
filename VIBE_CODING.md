# Nhật Ký Vibe Coding - NovelReader

Tài liệu này được lập theo tinh thần của Phụ lục A: "Quy định bắt buộc về triển khai đồ án theo phương pháp Vibe Coding". Mục tiêu là ghi nhận minh bạch phần việc có sử dụng AI hỗ trợ, vai trò của sinh viên trong việc đọc hiểu/chỉnh sửa/kiểm thử, và các giới hạn không để AI thay thế quyết định kỹ thuật cốt lõi.

## 1. Nguyên tắc áp dụng trong dự án

- AI được sử dụng như công cụ hỗ trợ kỹ thuật, không thay thế trách nhiệm thiết kế và bảo vệ đồ án.
- Sinh viên chịu trách nhiệm cuối cùng về kiến trúc, mã nguồn, cấu hình Firebase, dữ liệu và kết quả chạy app.
- Mọi phần code do AI gợi ý đều cần được đọc lại, chỉnh sửa theo bối cảnh dự án và kiểm thử bằng build/app debug.
- Các quyết định chính như chọn kiến trúc MVVM, chia 4 màn hình chính, dùng Room cho dữ liệu đọc offline, dùng Firebase cho cộng đồng và dùng Gemini API key cá nhân phải được sinh viên hiểu và giải thích được.

## 2. Phạm vi AI đã hỗ trợ

Ghi nhận bổ sung: phần mã nguồn khởi tạo đầu tiên của dự án được viết với sự hỗ trợ của Claude.ai. Sau giai đoạn khởi tạo, dự án tiếp tục được sinh viên đọc hiểu, chỉnh sửa, tái cấu trúc và mở rộng bằng quá trình làm việc thủ công kết hợp AI hỗ trợ. Các phần sửa lỗi, bổ sung chức năng và tài liệu hóa ở các giai đoạn sau có sử dụng ChatGPT/Codex như công cụ hỗ trợ kỹ thuật.

| Mảng kỹ thuật | AI sử dụng | Mục đích | Phần AI sinh/gợi ý | Phần sinh viên chỉnh/kiểm soát | Nhận xét |
|---|---|---|---|---|---|
| Khởi tạo mã nguồn ban đầu | Claude.ai | Tạo nền tảng code đầu tiên cho đồ án Android NovelReader | Sinh cấu trúc app ban đầu, một số màn hình, model và luồng chức năng cơ bản | Sinh viên tiếp tục đọc hiểu, chạy thử, phát hiện điểm chưa đúng, yêu cầu sửa và mở rộng theo mục tiêu đồ án | Cần khai báo rõ vì đây là phần AI hỗ trợ ở giai đoạn đầu của dự án |
| Kiến trúc ứng dụng | ChatGPT/Codex | Rà soát cấu trúc MVVM và đề xuất hướng chia lớp | Gợi ý phân tách `data`, `domain`, `presentation`, repository, ViewModel | Sinh viên quyết định giữ kiến trúc MVVM và điều chỉnh theo yêu cầu đồ án | AI hỗ trợ định hướng, không quyết định toàn bộ kiến trúc |
| Giá sách | ChatGPT/Codex | Sửa lỗi import TXT, tách chương và sắp xếp truyện | Gợi ý xử lý fallback khi regex không tách được chương, sắp xếp theo `lastReadAt` | Sinh viên kiểm tra bằng file TXT và luồng thêm/xóa/xem thông tin truyện | Đây là phần lõi cần hiểu rõ vì liên quan dữ liệu cục bộ |
| Trình đọc | ChatGPT/Codex | Cải thiện lưu tiến độ, đổi chương, hiển thị bản dịch | Gợi ý lưu vị trí cuộn tương đối, trạng thái Raw/Bản dịch, nút dịch lại | Sinh viên kiểm thử thao tác đọc, đổi chương, thoát vào lại | Cần giải thích được luồng `ReaderScreen -> ReaderViewModel -> ChapterRepository` |
| Dịch bằng Gemini | ChatGPT/Codex | Sửa request Gemini, lưu cache bản dịch và tên chương | Gợi ý schema JSON, lưu `translatedTitle`, `translatedContent`, chia nhỏ chương dài | Sinh viên nhập API key, prompt và kiểm thử bản dịch thực tế | AI chỉ hỗ trợ kỹ thuật gọi API; chất lượng prompt và kiểm thử thuộc sinh viên |
| Cộng đồng | ChatGPT/Codex | Xây dựng chat, chia sẻ truyện, prompt và bình luận | Gợi ý UI thread bình luận dạng hội thoại, Firebase Realtime Database node | Sinh viên cấu hình Firebase rules và kiểm thử quyền guest/user | Không dùng Firebase Storage vì giới hạn project miễn phí |
| Tài khoản cá nhân | ChatGPT/Codex | Hoàn thiện profile, avatar, cài đặt đọc và API key | Gợi ý giao diện profile, lưu DataStore, hiển thị avatar không dùng Storage | Sinh viên kiểm tra đăng ký/đăng nhập/cập nhật thông tin | Cần chú ý bảo mật API key và dữ liệu người dùng |
| Firebase rules | ChatGPT/Codex | Gợi ý cấu trúc quyền đọc/ghi | Gợi ý rule cho chat, prompt, truyện chia sẻ, comment, user role | Sinh viên thay đổi rule trên Firebase Console và test lỗi permission | Đây là phần phải tự hiểu khi bảo vệ |
| Giao diện Compose | ChatGPT/Codex | Cải thiện màn hình 4 tab, card bài đăng, reader toolbar | Gợi ý Compose component, Material 3, icon, dialog, bottom sheet | Sinh viên chỉnh theo yêu cầu UX và phản hồi thực tế | AI giúp tăng tốc viết UI nhưng không thay thế thiết kế trải nghiệm |
| Kiểm thử thủ công | ChatGPT/Codex | Chạy build, cài APK, đọc logcat | Gợi ý lệnh Gradle/ADB và cách lọc lỗi runtime | Sinh viên xác nhận trên emulator/thiết bị thật | Hiện chủ yếu là smoke test, cần bổ sung test tự động |
| Tài liệu dự án | ChatGPT/Codex | Viết README và nhật ký Vibe Coding | Gợi ý nội dung mô tả dự án, tiến độ, công nghệ, trách nhiệm AI | Sinh viên đọc lại, cập nhật đúng tiến độ thực tế trước khi nộp | Tài liệu cần được cập nhật khi chức năng thay đổi |

## 3. Những phần không giao cho AI quyết định

- Mục tiêu sản phẩm: app đọc, dịch và chia sẻ truyện chữ.
- Cấu trúc 4 menu chính: Giá sách, Khám phá, Cộng đồng, Cá nhân.
- Yêu cầu guest chỉ được xem/sử dụng nội dung công khai, không được đăng/chat/bình luận.
- Quyết định tạm hoãn phần crawl truyện để hoàn thiện các phần còn lại trước.
- Việc dùng Firebase miễn phí và tránh Firebase Storage.
- Prompt dịch mong muốn, API key cá nhân và cách kiểm thử chất lượng bản dịch.
- Quyết định cuối cùng về nội dung báo cáo và phần trình bày khi bảo vệ.

## 4. Quy trình làm việc với AI

1. Sinh viên mô tả vấn đề hoặc yêu cầu chức năng.
2. AI đọc cấu trúc code hiện có và đề xuất hướng sửa.
3. AI chỉnh code theo phạm vi yêu cầu.
4. Sinh viên chạy app, quan sát lỗi và phản hồi lại.
5. AI sửa tiếp dựa trên lỗi thực tế.
6. Sinh viên kiểm tra lại chức năng, đọc hiểu code và ghi nhận vào báo cáo.

## 5. Công việc sinh viên trực tiếp thực hiện/chỉnh sửa code

Để thể hiện sinh viên không chỉ sử dụng AI một cách thụ động, các phần sau cần được ghi nhận là công việc sinh viên trực tiếp đọc hiểu, chỉnh sửa, kiểm thử và có trách nhiệm giải trình:

- Đọc lại mã nguồn ban đầu do Claude.ai hỗ trợ sinh ra, xác định các phần chưa khớp với yêu cầu đồ án và lập danh sách lỗi cần sửa.
- Chỉnh sửa cấu trúc điều hướng 4 tab gồm Giá sách, Khám phá, Cộng đồng và Cá nhân để phù hợp mô tả sản phẩm.
- Chỉnh sửa luồng nhập file TXT, bao gồm đọc file, lấy tên truyện, tách chương theo regex và xử lý fallback khi không tách được chương.
- Chỉnh sửa phần giá sách để truyện vừa đọc được đưa lên đầu, tránh hiển thị trùng truyện và vẫn giữ menu xóa/xem thông tin.
- Chỉnh sửa `ReaderScreen` và `ReaderViewModel` để lưu tiến độ đọc, giữ vị trí tương đối khi chuyển chương và mở lại bản dịch nếu chương đã dịch.
- Chỉnh sửa model, entity, DAO và migration Room để lưu thêm `translatedTitle` cùng với `translatedContent`.
- Chỉnh sửa luồng gọi Gemini API, bao gồm định dạng JSON trả về, dịch cả tên chương, chia nhỏ chương dài và thêm nút dịch lại khi cache bản dịch bị lỗi.
- Chỉnh sửa phần cộng đồng: bỏ luồng tạo nhóm chat không cần thiết, chuyển bình luận của truyện chia sẻ/prompt sang trang hội thoại riêng.
- Chỉnh sửa cách lưu/hiển thị ảnh đại diện và ảnh bìa để phù hợp giới hạn không dùng Firebase Storage trong project miễn phí.
- Cấu hình và kiểm tra Firebase Authentication, Realtime Database rules, quyền guest/user/mod/admin.
- Chạy build, cài APK debug lên emulator, đọc logcat và ghi nhận lỗi runtime trước khi sửa tiếp.
- Tự kiểm tra các chức năng chính bằng thao tác thực tế: nhập truyện, đọc truyện, đổi chương, dịch chương, đăng nhập, chat, bình luận và chia sẻ truyện/prompt.

Khi bảo vệ, sinh viên cần có khả năng mở các file code liên quan và giải thích các chỉnh sửa trên, ví dụ `ReaderScreen.kt`, `ViewModels.kt`, `Repositories.kt`, `GeminiTranslationService.kt`, `Daos.kt`, `Entities.kt`, `CommunityScreen.kt` và cấu hình Firebase rules.

## 6. Các lệnh kiểm thử đã dùng trong quá trình phát triển

Thiết lập JDK 17:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17.0.19'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

Build Kotlin:

```powershell
.\gradlew.bat :app:compileDebugKotlin --offline --no-daemon --rerun-tasks
```

Build APK debug:

```powershell
.\gradlew.bat :app:assembleDebug --offline --no-daemon
```

Cài và mở app trên emulator:

```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -n com.example.novelreader/.MainActivity
```

Lọc log lỗi:

```powershell
& $adb logcat -d -t 500 | Select-String -Pattern "FATAL EXCEPTION|AndroidRuntime|com.example.novelreader|Room|SQLite|Gemini"
```

## 7. Trách nhiệm giải trình khi bảo vệ

Sinh viên cần chuẩn bị giải thích:

- Vì sao dùng MVVM cho dự án Android này.
- Vì sao dùng Room cho truyện/tiến độ đọc cục bộ.
- Vì sao dùng Firebase Realtime Database cho chat, bình luận và chia sẻ truyện.
- Vì sao không dùng Firebase Storage trong phiên bản hiện tại.
- Cách import TXT và tách chương hoạt động.
- Cách lưu tiến độ đọc và vị trí cuộn.
- Cách dịch chương bằng Gemini, lưu cache bản dịch và chuyển Raw/Bản dịch.
- Cách phân quyền guest/user/mod/admin trong cộng đồng.
- Những phần AI hỗ trợ và những phần sinh viên đã chỉnh sửa/kiểm thử.

## 8. Hạn chế hiện tại

- Kiểm thử tự động còn ít, chủ yếu đang kiểm thử thủ công bằng build, emulator và logcat.
- Phần crawl truyện từ website chưa phải trọng tâm hoàn thiện ở giai đoạn hiện tại.
- Chất lượng bản dịch phụ thuộc Gemini API, prompt người dùng và giới hạn token của model.
- Dữ liệu cộng đồng dùng Firebase Realtime Database nên cần rules chặt để tránh ghi dữ liệu sai.
- Việc lưu ảnh không dùng Firebase Storage có giới hạn về kích thước và hiệu năng.

## 9. Cam kết học thuật

Sinh viên cam kết không nộp code AI sinh ra mà không đọc hiểu. Các phần có AI hỗ trợ đã được chỉnh sửa theo yêu cầu đồ án, build kiểm tra và ghi nhận trong tài liệu này. Nếu được yêu cầu trong buổi bảo vệ, sinh viên phải có khả năng giải thích hoặc chỉnh sửa lại các phần code chính của dự án.
