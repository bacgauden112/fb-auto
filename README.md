# FB Auto — tự động thao tác trên app Facebook (Android)

App dùng quyền **Trợ năng (Accessibility)** để đọc màn hình app Facebook và tự bấm theo kịch bản:
rời nhóm, bỏ theo dõi Trang… Có nút nổi ▶/■ để chạy/dừng và nút 🔍 để dò tên các nút trên màn hình.

## 1. Tạo file APK (chọn 1 cách)

### Cách A — GitHub tự build (không cần cài gì)
1. Tạo repo mới trên github.com (để Private cũng được).
2. Bấm **uploading an existing file**, kéo **toàn bộ nội dung** thư mục `fb-auto` vào (gồm cả thư mục `.github`).
   - Nếu máy ẩn thư mục `.github`: tạo file mới trên GitHub tên `.github/workflows/build.yml` và dán nội dung file đó vào.
3. Vào tab **Actions** → chờ "Build APK" chạy xong (~3–5 phút).
4. Vào mục **Releases** (cột phải trang repo) → bản **latest** → tải `FBAuto.apk`.

### Cách B — Android Studio
Mở thư mục `fb-auto` bằng Android Studio → Build → Build APK(s).

## 2. Cài lên điện thoại
1. Chép `app-debug.apk` vào điện thoại, mở để cài (cho phép "Cài ứng dụng không rõ nguồn gốc").
   Play Protect có thể cảnh báo vì app dùng Trợ năng — chọn "Vẫn cài đặt".
2. Mở **FB Auto** → **Bật quyền Trợ năng** → Ứng dụng đã cài đặt → FB Auto → Bật.
   - Android 13+ nếu nút bị mờ: Cài đặt → Ứng dụng → FB Auto → ⋮ (góc phải) → **Cho phép cài đặt bị hạn chế**, rồi bật lại.
3. Một nút nổi màu đen sẽ hiện trên màn hình (kéo phần chữ để di chuyển).

## 3. Sử dụng
1. Trong app FB Auto: chọn **Mẫu** kịch bản → chỉnh tốc độ → **Lưu**.
2. Mở Facebook, vào đúng màn hình danh sách (ví dụ: Nhóm của bạn / Trang đang theo dõi).
3. Bấm **▶** trên nút nổi. Bấm **■** để dừng.

## 4. Khi kịch bản không chạy (Facebook đổi giao diện)
1. Mở đúng màn hình (hoặc mở menu ⋯ / hộp xác nhận), bấm **🔍**.
2. Quay lại app FB Auto → phần **Kết quả dò màn hình** sẽ liệt kê chữ và mô tả của từng nút (`[C]` = bấm được).
3. Sửa tên nút trong kịch bản cho khớp, hoặc gửi kết quả dò cho Claude để sửa giúp.

### Cú pháp kịch bản
```
* A | B     bước đầu: tìm mục trong danh sách có nút A hoặc B
* ROW       bước đầu: tự mở lần lượt từng hàng trong danh sách
! A | B     bỏ qua mục có chữ A hoặc B (vd. nhóm muốn giữ lại)
A | B       bấm nút A hoặc B (bắt buộc)
? A | B     bấm nếu có (không bắt buộc)
~A          khớp khi chữ CHỨA A
BACK        bấm Quay lại
WAIT 2      chờ 2 giây
RETURN      quay lại cho tới khi về đúng danh sách
# ...       ghi chú
```

## Lưu ý an toàn
- Facebook giới hạn số thao tác; làm quá nhanh có thể bị chặn tạm thời. Mặc định: chờ 8–20 giây, nghỉ 10 phút sau mỗi 15 mục, tối đa 50 mục/lần.
- App tự dừng khi thấy thông báo kiểu "Hãy thử lại sau" hoặc lỗi 3 lần liên tiếp.
- App chỉ hoạt động khi Facebook đang mở trên màn hình, và không gửi dữ liệu đi đâu.
