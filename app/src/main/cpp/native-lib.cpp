#include <jni.h>
#include <cstdint>

extern "C"
JNIEXPORT void JNICALL
        Java_com_xjyzs_operator_utils_Screenshot_scaleImageJNI(JNIEnv *env, jobject thiz,
                                                          jbyteArray src_array, jint src_w,
                                                          jint src_h,
                                                          jbyteArray dst_array, jint dst_w,
                                                          jint dst_h) {

// 使用 Critical 获取指针，阻止 JVM 垃圾回收，实现极速的内存直接访问 (Zero-Copy)
    auto *src = (jbyte *) env->GetPrimitiveArrayCritical(src_array, nullptr);
    auto *dst = (jbyte *) env->GetPrimitiveArrayCritical(dst_array, nullptr);

    if (src == nullptr || dst == nullptr) {
        if (src) env->ReleasePrimitiveArrayCritical(src_array, src, 0);
        if (dst) env->ReleasePrimitiveArrayCritical(dst_array, dst, 0);
        return;
    }

// RGBA_8888 格式每个像素占 4 字节，直接强转为 uint32_t 一次性操作，效率翻倍
    auto *src_32 = reinterpret_cast<uint32_t *>(src);
    auto *dst_32 = reinterpret_cast<uint32_t *>(dst);

// 计算缩放比例
    float x_ratio = static_cast<float>(src_w) / dst_w;
    float y_ratio = static_cast<float>(src_h) / dst_h;

// 缩放核心逻辑
    for (int i = 0; i < dst_h; i++) {
        int py = static_cast<int>(i * y_ratio);
        int src_row_offset = py * src_w;
        int dst_row_offset = i * dst_w;

        for (int j = 0; j < dst_w; j++) {
            int px = static_cast<int>(j * x_ratio);
// 直接复制 32bit 像素
            dst_32[dst_row_offset + j] = src_32[src_row_offset + px];
        }
    }

// 释放指针 (JNI_ABORT 表示不对 src 进行写回，0 表示将 dst 写回 Java 层)
    env->ReleasePrimitiveArrayCritical(src_array, src, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(dst_array, dst, 0);
}