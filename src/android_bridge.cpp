#include "android_bridge.h"

#include <SDL3/SDL_system.h>
#include <android/performance_hint.h>
#include <jni.h>
#include <unistd.h>
#include <rex/cvar.h>
#include <rex/perf/counter.h>
#include <rex/ui/vulkan/android_gpu_driver.h>

#include <array>
#include <filesystem>
#include <atomic>
#include <fstream>
#include <mutex>
#include <string>
#include <unordered_set>

#include "cheats.h"
#include "pinyon_shift_diagnostics.h"
#include "ui/settings_menu.h"
#include "ui/touch_pad.h"
extern "C" __attribute__((used, visibility("default")))
const char fh4droid_native_bridge_abi[] = "fh4droid-native-bridge:r7-budget-controls-v1";
extern "C" void rex_android_preparation_progress(uint64_t* values);
extern "C" uint64_t rex_android_preparation_stage();
namespace pinyon_shift::guest_native {
bool MobileNativeAudioOutputActive();
}
extern "C" JNIEXPORT jboolean JNICALL
Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeAudioOutputActive(JNIEnv*, jclass) {
  return pinyon_shift::guest_native::MobileNativeAudioOutputActive();
}
namespace {
std::mutex bridge_mutex;
std::mutex cpu_hint_mutex;
std::atomic<int64_t> cpu_frame_budget{0};
APerformanceHintSession* cpu_hint_session = nullptr;
int32_t cpu_hint_thread = 0;
int64_t cpu_hint_budget = 0;

void CloseCpuHintSession() {
  if (cpu_hint_session) APerformanceHint_closeSession(cpu_hint_session);
  cpu_hint_session = nullptr;
}
std::atomic<int> coarse_shading_available{-1};
std::function<void(bool)> menu_handler;
std::function<void()> settings_handler;
std::function<std::string()> achievements_handler;
std::function<void(const std::string&)> action_handler;
std::string Read(JNIEnv *env, jstring value) {
  if (!value) return {};
  const char *chars = env->GetStringUTFChars(value, nullptr);
  std::string result = chars ? chars : "";
  if (chars) env->ReleaseStringUTFChars(value, chars);
  return result;
}
}  // namespace
namespace pinyon_shift::android {
void ReportCpuFrame(int64_t work_ns) {
  if (cpu_frame_budget.load(std::memory_order_relaxed) == 0 || work_ns <= 0) return;
  std::lock_guard lock(cpu_hint_mutex);
  const int64_t target = cpu_frame_budget.load(std::memory_order_relaxed);
  if (target == 0 || work_ns > target * 8) return;
  const int32_t thread = gettid();
  if (thread != cpu_hint_thread || target != cpu_hint_budget) {
    CloseCpuHintSession();
    cpu_hint_thread = thread;
    cpu_hint_budget = target;
    if (auto* manager = APerformanceHint_getManager())
      cpu_hint_session = APerformanceHint_createSession(manager, &thread, 1, target);
  }
  if (cpu_hint_session && APerformanceHint_reportActualWorkDuration(cpu_hint_session, work_ns) != 0)
    CloseCpuHintSession();
}
void SetCoarseShadingAvailable(bool available){coarse_shading_available.store(available?1:0,std::memory_order_release);}
void RecordCleanExit(){rex::ui::vulkan::CallAndroidActivityMethod("recordCleanExit");}
void InstallBridge(std::function<void(bool)> menu,
                   std::function<void()> settings,
                   std::function<std::string()> achievements,
                   std::function<void(const std::string&)> action) {
  {
    std::lock_guard lock(bridge_mutex);
    menu_handler = std::move(menu);
    settings_handler = std::move(settings);
    achievements_handler = std::move(achievements);
    action_handler = std::move(action);
  }
  pinyon_shift::ui::TouchPad::Get().SetRumbleCallback(
      [](uint16_t left, uint16_t right) {
        auto *env = static_cast<JNIEnv *>(SDL_GetAndroidJNIEnv());
        if (!env) return;
        auto activity = static_cast<jobject>(SDL_GetAndroidActivity());
        if (!activity) return;
        jclass type = env->GetObjectClass(activity);
        jmethodID method =
            type ? env->GetMethodID(type, "gameRumble", "(II)V") : nullptr;
        if (method)
          env->CallVoidMethod(activity, method, jint(left), jint(right));
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (type) env->DeleteLocalRef(type);
        env->DeleteLocalRef(activity);
      });
}
void RefreshAchievements() {
  std::lock_guard lock(bridge_mutex);
  if (!achievements_handler) return;
  const auto snapshot = achievements_handler();
  auto path =
      pinyon_shift::diagnostics::StateRoot() / "cache/android-achievements.json";
  auto temporary = path;
  temporary += ".tmp";
  std::error_code ec;
  std::filesystem::create_directories(path.parent_path(), ec);
  if (ec) return;
  {
    std::ofstream out(temporary, std::ios::binary | std::ios::trunc);
    out << snapshot;
    out.flush();
    if (!out) return;
  }
  std::filesystem::rename(temporary, path, ec);
}
void ClearBridge() {
  coarse_shading_available.store(-1,std::memory_order_release);
  pinyon_shift::ui::TouchPad::Get().Rumble(0, 0);
  pinyon_shift::ui::TouchPad::Get().SetRumbleCallback({});
  std::lock_guard lock(bridge_mutex);
  menu_handler = {};
  settings_handler = {};
  achievements_handler = {};
  action_handler = {};
}
}  // namespace pinyon_shift::android
extern "C" JNIEXPORT void JNICALL
Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeHostMenu(
    JNIEnv *, jclass, jboolean active) {
  std::lock_guard lock(bridge_mutex);
  if (menu_handler) menu_handler(active);
}
extern "C" JNIEXPORT void JNICALL
Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeOpenSettings(JNIEnv *,
                                                                      jclass) {
  std::lock_guard lock(bridge_mutex);
  if (settings_handler) settings_handler();
}
extern "C" JNIEXPORT void JNICALL
Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeApplySetting(
    JNIEnv *env, jclass, jstring name, jstring value) {
  const auto key = Read(env, name), literal = Read(env, value);
  static const std::unordered_set<std::string> allowed = {
      "pinyon_shift_fh1_render_fps_limit",
      "pinyon_shift_fh1_shadows",
      "vulkan_texture_max_size",
      "pinyon_shift_fh1_env_map_rate",
      "present_effect",
      "disable_motion_blur",
      "disable_bloom",
      "anisotropic_override",
      "texture_mip_lod_bias",
      "swap_post_effect",
      "swap_post_effect_scene_only",
      "fh1_untile_predicated_tiling",
      "present_cas_additional_sharpness",
      "present_fsr_sharpness_reduction",
      "present_dither",
      "host_present_fps_limit",
      "host_present_sleep_spin",
      "disable_depth_of_field",
      "vsync",
      "present_letterbox",
      "hud_hidden",
      "fh1_hud_scale",
      "fh1_hud_safe_zone",
      "pinyon_shift_master_volume",
      "audio_mute",
      "pad_rumble_strength",
      "pad_gyro_steering",
      "pad_gyro_sensitivity",
      "pinyon_shift_ai_opponents"};
  static const std::unordered_set<std::string> extra = {
      "fh1_coarse_shading", "fh1_coarse_shading_alpha_test",
      "pinyon_shift_chase_height", "pinyon_shift_chase_distance",
      "pinyon_shift_chase_fov", "pinyon_shift_chase_stiffness",
      "pinyon_shift_camera_gforce", "pinyon_shift_camera_lean",
      "pinyon_shift_camera_shake", "pinyon_shift_camera_head",
      "pinyon_shift_fov_scale", "fh4droid_pad_inner",
      "fh4droid_pad_outer",     "fh4droid_pad_curve",
      "fh4droid_trigger_inner", "fh4droid_trigger_outer",
      "cheat_set_credits",      "cheat_time_scale",
      "cheat_time_of_day",      "cheat_day_length_minutes",
      "cheat_free_camera",      "cheat_show_collectibles",
      "cheat_any_car_events"};
  std::lock_guard lock(bridge_mutex);
  const bool cheat = key.starts_with("cheat_");
  if (menu_handler && (allowed.contains(key) || extra.contains(key)) &&
      (!cheat || pinyon_shift::cheats::Enabled()) && literal.size() < 80) {
    rex::cvar::SetFlagByName(key, literal);
    if (key == "pinyon_shift_master_volume")
      pinyon_shift::ui::ApplyMasterVolume();
  }
}
extern "C" JNIEXPORT jstring JNICALL
Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeSettingValue(
    JNIEnv *env, jclass, jstring name) {
  const auto key = Read(env, name);
  if (key.size() > 80 || !rex::cvar::GetFlagInfo(key)) return nullptr;
  return env->NewStringUTF(rex::cvar::GetFlagByName(key).c_str());
}
extern "C" JNIEXPORT jboolean JNICALL
Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeBridgeReady(JNIEnv *,
                                                                     jclass) {
  std::lock_guard lock(bridge_mutex);
  return bool(menu_handler);
}
extern "C" JNIEXPORT void JNICALL
Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeTilt(
    JNIEnv *, jclass, jfloat steering, jboolean enabled) {
  pinyon_shift::ui::TouchPad::Get().SetPhoneSteering(steering, enabled);
}
extern "C" JNIEXPORT void JNICALL
Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeReloadTouch(
    JNIEnv *env, jclass, jstring file, jboolean controller) {
  pinyon_shift::ui::TouchPad::Get().LoadLayout(Read(env, file));
  pinyon_shift::ui::TouchPad::Get().SetPhysicalController(controller);
}
extern "C" JNIEXPORT void JNICALL
Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeController(
    JNIEnv *, jclass, jboolean controller) {
  pinyon_shift::ui::TouchPad::Get().SetPhysicalController(controller);
}

extern "C" JNIEXPORT jlong JNICALL
Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeFrameCount(JNIEnv *,
                                                                    jclass) {
  return jlong(rex::perf::SourceFrameCount());
}
extern "C" JNIEXPORT jint JNICALL
Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeCoarseShadingSupport(JNIEnv*,jclass){return coarse_shading_available.load(std::memory_order_acquire);}
extern "C" JNIEXPORT jlongArray JNICALL
Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativePreparation(JNIEnv* env,jclass) {
  uint64_t progress[5]{};rex_android_preparation_progress(progress);
  jlong values[6];for(size_t i=0;i<5;i++)values[i]=jlong(progress[i]);
  values[5]=jlong(rex_android_preparation_stage());
  auto out=env->NewLongArray(6);if(out)env->SetLongArrayRegion(out,0,6,values);return out;
}
extern "C" JNIEXPORT void JNICALL
Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeCpuHintTarget(JNIEnv*,jclass,jint fps) {
  std::lock_guard lock(cpu_hint_mutex);
  cpu_frame_budget.store(fps >= 15 && fps <= 240 ? 1000000000LL / fps : 0,
                         std::memory_order_relaxed);
  if (!fps) {
    CloseCpuHintSession();
    cpu_hint_thread = 0;
    cpu_hint_budget = 0;
  }
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeFrameHistory(JNIEnv* env, jclass) {
  constexpr size_t kFrames = 360;
  constexpr size_t kFields = rex::perf::kAndroidFrameHistoryFields;
  std::array<int64_t, kFrames * kFields> history;
  const size_t count = rex::perf::CopyAndroidFrameHistory(history.data(), kFrames);
  std::array<jlong, kFrames * kFields> values;
  for (size_t i = 0; i < count * kFields; ++i) values[i] = jlong(history[i]);
  auto output = env->NewLongArray(jsize(count * kFields));
  if (output) env->SetLongArrayRegion(output, 0, jsize(count * kFields), values.data());
  return output;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativePerformance(JNIEnv *env,jclass) {
  const jlong values[] = {
      rex::perf::GetSnapshotCounter(rex::perf::CounterId::kFrameTimeUs),
      jlong(rex::perf::ProcessResidentBytes()/1048576),
      rex::perf::GetSnapshotCounter(rex::perf::CounterId::kMemoryDeviceUsageMb),
      rex::perf::GetSnapshotCounter(rex::perf::CounterId::kMemoryDeviceBudgetMb),
      rex::perf::GetSnapshotCounter(rex::perf::CounterId::kProcessCpuNs),
      rex::perf::GetSnapshotCounter(rex::perf::CounterId::kGuestFrameGpuTimeNs),
      rex::perf::GetSnapshotCounter(rex::perf::CounterId::kGuestFrameGpuTimingSamples),
      rex::perf::GetSnapshotCounter(rex::perf::CounterId::kTextureCacheMb)};
  auto out=env->NewLongArray(8);
  if(out)env->SetLongArrayRegion(out,0,8,values);
  return out;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeMenuContext(JNIEnv *,jclass) {
  return pinyon_shift::ui::TouchPad::Get().MenuContext();
}
extern "C" JNIEXPORT jstring JNICALL
Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeAchievements(
    JNIEnv *env, jclass) {
  std::lock_guard lock(bridge_mutex);
  const std::string json = achievements_handler ? achievements_handler() : "{}";
  return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativePressedControls(JNIEnv* env,jclass){
  const auto labels=pinyon_shift::ui::TouchPad::Get().PressedControls();
  return env->NewStringUTF(labels.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeAction(JNIEnv* env,jclass,jstring value) {
  const auto action = Read(env, value);
  if (action != "achievements" && action != "quit") return;
  std::lock_guard lock(bridge_mutex);
  if (action_handler) action_handler(action);
}
