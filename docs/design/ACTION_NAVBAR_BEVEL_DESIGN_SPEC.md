# Action Navbar (ProductDetailModal) Design Specification & Bevel System

• **Status**: Accepted  
• **Date**: 2026-09-23  
• **Component File**: `ProductPage.tsx:4982-5170`  
• **Bevel System**: `bevel.tsx:5-113`  
• **Selector**: `div.absolute.bottom-5:nth-of-type(2)`  
• **Render Context**: Modal chi tiết sản phẩm (`ProductDetailModal`) trên route `/p`  

---

## 1. Context & Tổng quan Kiến trúc

Thanh Floating Action Navbar (Dock hành động cố định chân modal) là trung tâm điều hướng mua hàng nhanh của `ProductDetailModal`. Khi người dùng mở modal trên `/p`, thanh Navbar chính ở đầu trang tự động chuyển sang chế độ ẩn (`isAutoHideMode`), nhường toàn bộ tiêu điểm tương tác ở đáy màn hình cho thanh Floating Action Dock này.

Thanh dock được thiết kế theo ngôn ngữ **Physical Glassmorphism & Optical Bevel**, kết hợp kính mờ đa tầng, vát viền quang học (CNC Chamfer) và bóng đổ ambient ám sắc cam thương hiệu (`#FF4D24`).

---

## 2. Đặc tả Chi tiết 6 Yếu tố Thiết kế

### 2.1. Độ bo cong (Border Radii Specification)

| Thành phần | Class Tailwind | Giá trị | Ý đồ thiết kế |
| :--- | :--- | :--- | :--- |
| **Khung dock chính (Outer Shell)** | `rounded-2xl` | 16px (hoặc 18px theo CSS variable) | Bo tròn dạng viên thuốc mở rộng, ôm trọn nội dung mà không thô cứng |
| **Lớp ambient tint bên trong** | `rounded-2xl` | 16px | Trùng khít với mép vỏ ngoài |
| **Tag Ribbon Giảm giá (3D Ribbon)** | `rounded-br-lg rounded-tr-sm` | Dưới-phải: 8px, Trên-phải: 2px, Trái: 0px | Tạo mép ribbon gấp tràn qua cạnh trái của dock |
| **Nếp gập 3D của Ribbon (Corner fold)** | Góc cắt đa giác | `clip-path: polygon(100% 0, 0 0, 100% 100%)` | Hình tam giác vuông 4 × 4px giả lập nếp gấp ra sau thanh dock |
| **Hộp ảnh Thumbnail sản phẩm** | `rounded-l-2xl` | Trái: 16px, Phải: 0px | Bo cong đồng trục với góc trái của dock vỏ ngoài, tràn viền sạch |
| **Nút "Trả góp 0%"** | `rounded-xl` | 12px | Nút bấm phụ kích thước gọn gàng |
| **Nút "MUA NGAY" (BevelButton)** | `rounded-xl` | 12px | Khối nút bấm chính dập nổi vát cạnh |
| **Nút "Thêm giỏ hàng" (motion.button)** | `rounded-xl` | 12px | Nút icon tỉ lệ 1:1 (40 × 40px) |
| **Sóng xung kích nút giỏ hàng (Ripple)** | `rounded-xl` | 12px | Giữ nguyên phom nút khi bung toả hiệu ứng |
| **Huy hiệu số lượng giỏ hàng (cart-hat-count)** | `rounded-full` | 9999px (22 × 22px) | Bo tròn tuyệt đối dạng viên ngọc nổi trên góc nút |
| **Progressive Blur đáy vùng cuộn** | `rounded-b-2xl` | 16px | Hòa lẫn mượt mà nội dung cuộn bên dưới trước khi chạm vào dock |

---

### 2.2. Opacity & Độ xuyên thấu Đa tầng (Alpha Transparencies)

| Vị trí | Giá trị Opacity / Class | Chi tiết kỹ thuật |
| :--- | :--- | :--- |
| **Nền dock (Light Theme)** | `bg-white/75` (75%) | Nền trắng kính mờ, hiển thị 25% nội dung chuyển động bên dưới |
| **Nền dock (Dark Theme)** | `dark:bg-zinc-900/80` (80%) | Nền tối sâu thẳm chống chói khi lướt đêm |
| **Vát đỉnh dock (Top Edge Highlight)** | `border-t-white/95` (95%) | Đường viền trắng gần như đặc tạo phản quang specular mép trên |
| **Chân đáy dock (Bottom Edge Shadow)** | `border-b-slate-400/40` (40%) | Đường viền tối mờ mô phỏng bóng đổ chân đế |
| **Hai cạnh sườn dock** | `border-x-white/70` (70%) / `dark:border-white/20` (20%) | Chuyển tiếp mượt mà giữa viền sáng đỉnh và viền tối đáy |
| **Màng ấm thương hiệu (Ambient Tint)** | `from-primary/[0.04]` (4%) | Ám sắc cam siêu nhẹ (4%) từ trái sang phải, không làm đục kính |
| **Chữ giá cũ gạch ngang** | `text-muted-foreground/75` (75%) | Hạ tông màu xám xuống 75% để nhường tương phản cho giá chính |
| **Nút Trả góp / Giỏ hàng (Nền kính)** | `from-white/95 via-white/85 to-white/70` | 3 tầng gradient kính từ 95% xuống 70% |
| **Viền nút kính phụ** | `border-primary/60` (60%) | Viền cam mờ 60% làm nổi khung nút |
| **Hover nút kính phụ** | `hover:from-orange-500/[0.08] hover:to-orange-500/[0.03]` | Nhuộm sắc cam đào 8% → 3% khi hover |
| **Viền nút MUA NGAY** | `border-t-white/50`, `border-x-[#FF4D24]/80` | Viền sáng đỉnh 50% trắng, sườn 80% cam |
| **Rãnh sáng trong nút MUA NGAY** | `inset_0_1px_0_rgba(255,255,255,0.45)` (45%) | Rãnh bevel phản quang 45% trắng phía trong nút |
| **Nền icon giỏ hàng khi đã thêm** | `fill-[#FF4D24]/15` (15%) | Tô màu lòng giỏ hàng 15% opacity cam |
| **Sóng bung nút giỏ hàng** | `opacity: 0.75 → 0` | Vòng viền cam tan biến khi kích hoạt |
| **Sóng bung huy hiệu số lượng** | `opacity: 0.90 → 0` | Hào quang tan biến khi tăng số lượng |

---

### 2.3. Hệ thống Typography

• **Font Family chung**: `font-sans` (`Geist Variable`, `-apple-system`, `BlinkMacSystemFont`, `Segoe UI`, `Roboto`, `sans-serif`).

| Thành phần | Cấu hình Typeface | Cỡ chữ & Trọng số | Line-height & Trình bày |
| :--- | :--- | :--- | :--- |
| **Tiêu đề sản phẩm (Title)** | `font-sans font-bold text-foreground` | Mobile: 12.5px, Desktop: 13.5px (Bold 700) | `leading-tight`, truncate (tối đa 170px / 260px / 335px) |
| **Giá khuyến mãi (Current Price)** | `font-sans font-black text-primary` | Mobile: 15px, Desktop: 16px (Black 900) | `leading-none`, màu cam chủ đạo `#FF4D24` |
| **Giá niêm yết cũ (Old Price)** | `font-sans font-medium text-muted-foreground/75` | Mobile: 11px, Desktop: 11.5px (Medium 500) | `leading-none`, `line-through` gạch ngang |
| **Nhãn Ribbon "Giảm 10%"** | `text-white font-black` | 10.5px (Black 900) | Chữ trắng đậm đặc biệt trên nền cam |
| **Nút "Trả góp 0%"** | `font-sans font-black text-primary` | Mobile: 11.5px, Desktop: 12px (Black 900) | `whitespace-nowrap`, không xuống hàng |
| **Nút "MUA NGAY"** | `font-sans font-black text-white` | Mobile: 11.5px, Desktop: 12px (Black 900) | `uppercase`, `tracking-wider` dãn chữ 0.05em |
| **Số lượng giỏ hàng (Badge Count)** | `font-sans font-black text-white` | 10.5px (Black 900) | Canh giữa chuẩn trong huy hiệu tròn |

---

### 2.4. Hiệu ứng Ám màu & Quang học (Color Tinting, Optics & Shadows)

1. **Hiệu ứng Màng sáng Thương hiệu (Ambient Brand Tint)**:
   ```html
   <div aria-hidden="true" class="pointer-events-none absolute inset-0 rounded-2xl bg-gradient-to-r from-primary/[0.04] via-transparent to-transparent" />
   ```
   *Tác dụng*: Ám 4% sắc cam từ cạnh trái, tạo cảm giác thanh dock hấp thụ năng lượng quang học của sản phẩm mà không làm bẩn nền kính trong suốt.

2. **Khuếch đại Độ bão hoà Quang học (Optical Saturation Boost)**:
   * Lớp lọc kép: `backdrop-blur-2xl backdrop-saturate-200`.
   * *Tác dụng*: `backdrop-saturate-200` nhân đôi độ bão hoà màu của các phần tử cuộn bên dưới thanh dock, chống xỉn màu.

3. **Hệ thống Bóng đổ Đa tầng Ambient / Colored Glow / Elevation**:
   ```css
   box-shadow:
     0 25px 60px -15px rgba(0, 0, 0, 0.20),     /* Tầng 1: Elevation lơ lửng z-axis */
     0 10px 25px -5px  rgba(255, 77, 36, 0.12),   /* Tầng 2: Ám màu hào quang cam thương hiệu */
     inset 0 1px 0     rgba(255, 255, 255, 1.0);  /* Tầng 3: Rãnh phản quang đỉnh CNC */
   ```

4. **Hiệu ứng Ám màu khi Hover (Interactive Tinting)**:
   * Gradient đổi từ trắng sang ấm: `hover:from-orange-500/[0.08] hover:to-orange-500/[0.03]`.
   * Tỏa ánh sáng cam: `hover:shadow-[0_4px_12px_rgba(255,77,36,0.18),inset_0_1px_0_rgba(255,255,255,1)]`.

5. **Nút "MUA NGAY" Primary Glow**:
   * Shadow: `shadow-[0_4px_16px_rgba(255,77,36,0.35),inset_0_1px_0_rgba(255,255,255,0.45)]`
   * Nền dải màu 3 trạm rực rỡ: `from-[#FF5E3A] via-[#FF4D24] to-[#E03A12]`.

---

### 2.5. Hoạt ảnh Liên quan (Motion, Springs, Keyframes & Genie Fly)

* **A. Đổi biến thể máy**: `AnimatePresence` kết hợp Optical Blur (0.18s).
* **B. Chuyển động nhảy giá tiền**: Spring pop (`stiffness: 450, damping: 28`).
* **C. Rung nảy nút giỏ hàng**: Keyframes 5 nhịp nảy (`scale: [1, 0.86, 1.2, 0.94, 1.05, 1]`).
* **D. Vòng sóng xung kích năng lượng**: Scale `0.8` → `1.85`, opacity `0.75` → `0`.
* **E. Hiệu ứng Genie Fly to Cart**: Đường bay cong parabol từ hero image xuống nút giỏ hàng (480ms).
* **F. Huy hiệu số lượng đàn hồi**: Scale & rotation pop sequence.

---

### 2.6. Hiệu ứng Bevel trên Khung nút bấm & Vỏ dock

* **Top Edge Specular Rim (`border-t`)**: Nguồn sáng dọi thẳng từ trên xuống.
* **Bottom Edge Ambient Occlusion Rim (`border-b`)**: Bóng tối chân đế khi mép nút gấp góc.
* **Lateral Edge Connectors (`border-x`)**: Chuyển tiếp sắc độ hai cạnh bên.
* **Specular Inset Ridge (`inset 0 1px 0`)**: Rãnh phản quang dập rãnh bên trong nút bấm.
