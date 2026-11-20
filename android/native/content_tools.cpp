#include <jni.h>
#include <filesystem>
#include <sstream>
#include <string>
#include <stdexcept>
#include <toml++/toml.hpp>
#include "content/dlc_extract.h"

namespace {
std::string Read(JNIEnv* env, jstring value) {
  if (!value) throw std::runtime_error("Missing content path");
  const jchar* chars = env->GetStringChars(value, nullptr);
  if (!chars) throw std::runtime_error("Content path unavailable");
  std::u16string path(reinterpret_cast<const char16_t*>(chars),
                      env->GetStringLength(value));
  env->ReleaseStringChars(value, chars);
  return std::filesystem::path(path).string();
}
void Error(JNIEnv* env, const std::exception& error) {
  if (env->ExceptionCheck()) return;
  jclass type = env->FindClass("java/io/IOException");
  if (type) { env->ThrowNew(type, error.what()); env->DeleteLocalRef(type); }
}

class ContentWorker {
 public:
  explicit ContentWorker(JNIEnv* env) : env_(env) {
    jclass type = env_->FindClass("java/lang/Thread");
    if (!type) throw std::runtime_error("Content worker is unavailable");
    auto current = env_->GetStaticMethodID(type, "currentThread", "()Ljava/lang/Thread;");
    interrupted_ = env_->GetMethodID(type, "isInterrupted", "()Z");
    if (current && interrupted_) thread_ = env_->CallStaticObjectMethod(type, current);
    env_->DeleteLocalRef(type);
    if (!thread_) throw std::runtime_error("Content worker is unavailable");
  }
  ~ContentWorker() { env_->DeleteLocalRef(thread_); }

  bool cancelled() const { return env_->CallBooleanMethod(thread_, interrupted_) == JNI_TRUE; }

 private:
  JNIEnv* env_;
  jobject thread_ = nullptr;
  jmethodID interrupted_ = nullptr;
};
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_studio_deimos_pinyonshift_ContentNative_nativeManifest(JNIEnv* env, jclass,
                                                          jstring path) {
  try {
    const auto file = std::filesystem::path(Read(env, path));
    if (!std::filesystem::is_regular_file(file) || std::filesystem::is_symlink(file) ||
        std::filesystem::file_size(file) > 262144)
      throw std::runtime_error("Mod manifest is missing or too large");
    auto manifest = toml::parse_file(file.string());
    std::ostringstream json;
    json << toml::json_formatter{manifest};
    const auto bytes = json.str();
    auto out = env->NewByteArray(jsize(bytes.size()));
    if (out) env->SetByteArrayRegion(out, 0, jsize(bytes.size()), reinterpret_cast<const jbyte*>(bytes.data()));
    return out;
  } catch (const std::exception& error) { Error(env, error); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL
Java_studio_deimos_pinyonshift_ContentNative_nativeExtractDlc(JNIEnv* env, jclass,
    jstring package, jstring destination, jstring id) {
  try {
    ContentWorker worker(env);
    pinyon_shift::content::ExtractVerifiedDlc(Read(env, package), Read(env, destination),
        Read(env, id), [&worker] { return worker.cancelled(); });
  } catch (const std::exception& error) { Error(env, error); }
}

extern "C" JNIEXPORT jlong JNICALL
Java_studio_deimos_pinyonshift_ContentNative_nativeExtractForzaProfile(JNIEnv* env, jclass,
    jstring package, jstring destination) {
  try {
    ContentWorker worker(env);
    return static_cast<jlong>(pinyon_shift::content::ExtractForzaProfile(
        Read(env, package), Read(env, destination), [&worker] { return worker.cancelled(); }));
  } catch (const std::exception& error) { Error(env, error); return 0; }
}
