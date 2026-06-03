package com.example.oanquan.engine;

/**
 * Một bước animation gửi về frontend.
 * - SOW: rải 1 quân từ fromIndex sang toIndex.
 * - CAPTURE: ăn toàn bộ quân ở toIndex sau khi có một ô trống ở fromIndex.
 * pickupIndex dùng để frontend biết ô nào vừa được bốc hết quân, tránh render sai/giật.
 */
public record AnimationStep(
        int order,
        int fromIndex,
        int toIndex,
        String action,
        Integer pickupIndex,
        String notice
) {
    public AnimationStep(int order, int fromIndex, int toIndex, String action) {
        this(order, fromIndex, toIndex, action, null, null);
    }
}
