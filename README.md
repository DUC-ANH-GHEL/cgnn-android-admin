# CGNN Admin NFC (Android)

Ứng dụng Android quản trị màn **Thẻ kích hoạt máy** (`/admin/may-cua-bac/the-nfc`) của hệ thống CGNN (`https://cgnn.vn`).

## Tính năng chính
- **Đăng nhập quản trị**: Đăng nhập bằng tài khoản admin/staff/kỹ thuật (`POST /api/v1/users/login`), tự lưu phiên bảo mật (`EncryptedSharedPreferences`) và tự làm mới token (`POST /api/v1/users/refresh`).
- **Danh sách & bộ lọc thẻ NFC**:
  - Tìm kiếm theo tên khách, số điện thoại, mã thẻ, mã đơn (có chế độ tìm toàn bộ hoặc trong bộ lọc).
  - Lọc theo tình trạng thẻ (`Chưa gắn máy`, `Đã gắn máy`, `Đã tắt`, `Đã thay`), đơn mua thẻ (20 trạng thái Pancake + chưa có đơn), và thẻ cần xem.
  - Kéo để làm mới & tự đồng bộ nền mỗi 30 giây.
- **Nạp thẻ NFC native Android (`NfcAdapter.enableReaderMode`)**:
  - Nạp 1 thẻ hoặc nạp hàng loạt nhiều thẻ đã chọn.
  - Tự định dạng thẻ trắng (`NdefFormatable`) hoặc ghi đè thẻ `Ndef`, đọc kiểm tra trực tiếp ngay sau khi ghi, chống ghi đè nhầm thẻ cũ bằng UID chip, rung phản hồi và gửi nhật ký nạp lên máy chủ.
- **Chi tiết thẻ & thao tác đầy đủ**:
  - Chép nhanh mã kích hoạt, link thẻ, nội dung tin nhắn Pancake.
  - Tạo lô thẻ mới (1–500 thẻ), tạo 12 mã full quyền (chia sẻ file JSON), xuất CSV (theo bộ lọc hoặc thẻ đã chọn).
  - Thêm số điện thoại tạo mã, gắn đơn mua, gắn máy cũ, chuyển sang tài khoản khác, tách sang tài khoản riêng, bật/tắt thẻ, sửa quyền thẻ (đơn lẻ & hàng loạt), cài đặt quyền mặc định cho thẻ mới.
  - Cập nhật / tạo mới **Hồ sơ máy** gắn với thẻ (tìm khách hàng, hãng/dòng máy, giờ máy, km, bảo dưỡng, vụ mùa, lịch làm việc).
  - Đồng bộ tất cả tin nhắn sang Pancake với tiến độ trực tiếp.
  - **In tờ hướng dẫn 75×130mm** có mã QR tự tạo offline qua Android `PrintManager`, kèm màn hình sửa nội dung tờ hướng dẫn và xem trước trực tiếp.

## Build APK

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\gradlew.bat assembleDebug
```

File APK debug được tạo tại: `app/build/outputs/apk/debug/app-debug.apk`.
