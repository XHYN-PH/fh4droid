#include "android_bridge.h"
#include "ui/touch_pad.h"
#include "ui/settings_menu.h"
#include <jni.h>
#include <rex/cvar.h>
#include <rex/ui/vulkan/android_gpu_driver.h>
#include <mutex>
#include <string>
#include <unordered_set>
namespace {
std::mutex bridge_mutex;
std::function<void(bool)> menu_handler;
std::function<void()> settings_handler;
std::string Read(JNIEnv* env,jstring value){
  if(!value)return {};const char* chars=env->GetStringUTFChars(value,nullptr);
  std::string result=chars?chars:"";if(chars)env->ReleaseStringUTFChars(value,chars);return result;
}
}
namespace pinyon_shift::android {
void RecordCleanExit(){rex::ui::vulkan::CallAndroidActivityMethod("recordCleanExit");}
void InstallBridge(std::function<void(bool)> menu,std::function<void()> settings){
  std::lock_guard lock(bridge_mutex);menu_handler=std::move(menu);settings_handler=std::move(settings);
}
void ClearBridge(){std::lock_guard lock(bridge_mutex);menu_handler={};settings_handler={};}
}
extern "C" JNIEXPORT void JNICALL Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeHostMenu(JNIEnv*,jclass,jboolean active){
  std::lock_guard lock(bridge_mutex);if(menu_handler)menu_handler(active);
}
extern "C" JNIEXPORT void JNICALL Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeOpenSettings(JNIEnv*,jclass){
  std::lock_guard lock(bridge_mutex);if(settings_handler)settings_handler();
}
extern "C" JNIEXPORT void JNICALL Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeApplySetting(JNIEnv* env,jclass,jstring name,jstring value){
  const auto key=Read(env,name),literal=Read(env,value);
  static const std::unordered_set<std::string> allowed={
    "pinyon_shift_fh1_render_fps_limit","pinyon_shift_fh1_shadows","vulkan_texture_max_size",
    "pinyon_shift_fh1_env_map_rate","present_effect","disable_motion_blur","disable_bloom",
    "anisotropic_override","texture_mip_lod_bias","swap_post_effect","swap_post_effect_scene_only",
    "fh1_untile_predicated_tiling","disable_depth_of_field","vsync","present_letterbox",
    "hud_hidden","fh1_hud_scale","fh1_hud_safe_zone","pinyon_shift_master_volume","audio_mute",
    "pad_rumble_strength","pad_gyro_steering","pad_gyro_sensitivity","pinyon_shift_ai_opponents"};
  std::lock_guard lock(bridge_mutex);
  if(menu_handler&&allowed.contains(key)&&literal.size()<80) {
    rex::cvar::SetFlagByName(key,literal);
    if(key=="pinyon_shift_master_volume")pinyon_shift::ui::ApplyMasterVolume();
  }
}
extern "C" JNIEXPORT void JNICALL Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeReloadTouch(JNIEnv* env,jclass,jstring file,jboolean controller){
  pinyon_shift::ui::TouchPad::Get().LoadLayout(Read(env,file));
  pinyon_shift::ui::TouchPad::Get().SetPhysicalController(controller);
}
extern "C" JNIEXPORT void JNICALL Java_studio_deimos_pinyonshift_PinyonShiftActivity_nativeController(JNIEnv*,jclass,jboolean controller){
  pinyon_shift::ui::TouchPad::Get().SetPhysicalController(controller);
}
