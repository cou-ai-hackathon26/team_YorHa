#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_medidiary_app_ai_NativeBridge_testJni(JNIEnv* env, jobject /* this */) {
    std::string hello = "Hello from MediDiary C++ Native Pipeline!";
    return env->NewStringUTF(hello.c_str());
}
