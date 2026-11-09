#include "ui/touch_pad.h"
#include "ui/touch_icons.h"

#include <algorithm>
#include <cmath>
#include <fstream>
#include <sstream>

#include <rex/logging.h>
#include <rex/ui/ui_event.h>
#include <rex/ui/window.h>

namespace pinyon_shift::ui {
namespace {

using rex::input::DeviceId;
using rex::X_RESULT;
using rex::X_STATUS;

constexpr DeviceId kTouchDevice = static_cast<DeviceId>(0x54434850);  // TCHP
// Hidden this long after the last touch: a controller player never sees it.
// The stick's travel, as a fraction of the window height.
constexpr float kStickTravel = 0.11f;

class TouchPadDriver final : public rex::input::InputDriver {
 public:
  explicit TouchPadDriver(TouchPad& pad) : InputDriver(nullptr, 0), pad_(pad) {}
  X_STATUS Setup() override { return X_STATUS_SUCCESS; }
  void EnumerateDevices(std::vector<rex::input::DeviceInfo>& out) override {
    rex::input::DeviceInfo info;
    info.id = kTouchDevice;
    info.name = "Touch screen controls";
    info.synthetic = true;
    out.push_back(std::move(info));
  }
  X_RESULT GetDeviceState(DeviceId id, rex::input::X_INPUT_STATE* out) override {
    if (id != kTouchDevice) {
      return X_ERROR_DEVICE_NOT_CONNECTED;
    }
    if (out) {
      *out = {};
      out->gamepad = pad_.State();
      out->packet_number = ++packet_;
    }
    return X_ERROR_SUCCESS;
  }
  X_RESULT GetDeviceCapabilities(DeviceId id, uint32_t,
                                 rex::input::X_INPUT_CAPABILITIES* out) override {
    if (id != kTouchDevice) {
      return X_ERROR_DEVICE_NOT_CONNECTED;
    }
    if (out) {
      *out = {};
      out->type = 1;
      out->sub_type = 1;
      out->flags = 1;  // XINPUT_CAPS_FFB_SUPPORTED
      out->gamepad.buttons = UINT16_MAX;
      out->gamepad.left_trigger = UINT8_MAX;
      out->gamepad.right_trigger = UINT8_MAX;
      out->gamepad.thumb_lx = INT16_MAX;
      out->gamepad.thumb_ly = INT16_MAX;
      out->gamepad.thumb_rx = INT16_MAX;
      out->gamepad.thumb_ry = INT16_MAX;
    }
    return X_ERROR_SUCCESS;
  }
  X_RESULT SetDeviceVibration(DeviceId id, rex::input::X_INPUT_VIBRATION* value) override {
    if(id == kTouchDevice && value) pad_.Rumble(value->left_motor_speed,value->right_motor_speed);
    return id == kTouchDevice ? X_ERROR_SUCCESS : X_ERROR_DEVICE_NOT_CONNECTED;
  }
  X_RESULT GetDeviceKeystroke(DeviceId id, uint32_t, rex::input::X_INPUT_KEYSTROKE*) override {
    return id == kTouchDevice ? X_ERROR_EMPTY : X_ERROR_DEVICE_NOT_CONNECTED;
  }

 private:
  TouchPad& pad_;
  uint32_t packet_ = 0;
};

}  // namespace

TouchPad& TouchPad::Get() {
  static TouchPad pad;
  return pad;
}

const std::vector<TouchPad::Layout>& TouchPad::Controls() {
  // Thumb reach on a landscape phone or handheld: throttle under the right
  // thumb with brake beside it, face buttons above, arrows in the lower left.
  // The optional stick retains its own saved position and travel.
  static const std::vector<Layout> controls = {
      {Control::kThrottle, "RT", 0.905f, 0.78f, 0.13f},
      {Control::kBrake, "LT", 0.745f, 0.84f, 0.10f},
      {Control::kA, "A", 0.83f, 0.52f, 0.075f},
      {Control::kB, "B", 0.93f, 0.43f, 0.075f},
      {Control::kX, "X", 0.73f, 0.43f, 0.075f},
      {Control::kY, "Y", 0.83f, 0.34f, 0.075f},
      {Control::kLeftBumper, "LB", 0.07f, 0.20f, 0.075f},
      {Control::kRightBumper, "RB", 0.93f, 0.20f, 0.075f},
      {Control::kBack, "BACK", 0.40f, 0.08f, 0.06f},
      {Control::kStart, "START", 0.60f, 0.08f, 0.06f},
      {Control::kStick, "LS", 0.16f, 0.74f, kStickTravel},
      {Control::kSteerLeft, "LEFT", 0.075f, 0.80f, 0.10f},
      {Control::kSteerRight, "RIGHT", 0.215f, 0.80f, 0.10f},
      {Control::kRightStick, "RS", 0.535f, 0.80f, 0.085f},
      {Control::kDpadUp, "UP", 0.30f, 0.33f, 0.05f},
      {Control::kDpadDown, "DOWN", 0.30f, 0.55f, 0.05f},
      {Control::kDpadLeft, "DPAD_LEFT", 0.24f, 0.44f, 0.05f},
      {Control::kDpadRight, "DPAD_RIGHT", 0.36f, 0.44f, 0.05f},
      {Control::kLeftStickClick, "L3", 0.10f, 0.43f, 0.05f},
      {Control::kRightStickClick, "R3", 0.535f, 0.53f, 0.05f},
  };
  return controls;
}

void TouchPad::LoadLayout(const std::filesystem::path& path) {
  {
    std::lock_guard lock(mutex_);
    controls_ = Controls(); mapping_.clear(); hidden_.assign(controls_.size(), false);
    timeouts_.assign(controls_.size(),-1);activity_.assign(controls_.size(),{});
    opacity_ = 0.55f; icon_opacity_ = .95f; auto_hide_ = true; arrow_steering_ = true; fingers_.clear();
    analog_pedals_ = false; high_contrast_ = false; steering_response_ms_ = 0;
    steering_return_ms_ = -1; steering_lock_ = 1.f;
    steering_value_ = 0; steering_updated_ = {};
    idle_seconds_=20;left_mode_=right_mode_=0;driving_icons_=button_hints_=true;
    std::ifstream in(path);
    bool extended = false;
    for (std::string line; std::getline(in, line);) {
      std::istringstream row(line); std::string name; row >> name;
      if (name == "steering_buttons" || name == "STEER-L" || name == "STEER-R") {
        extended = true; break;
      }
    }
    in.clear(); in.seekg(0);
    for (std::string line; std::getline(in, line);) {
      std::istringstream row(line); std::string label; row >> label;
      if (label == "opacity") { float value; if (row >> value && std::isfinite(value)) opacity_ = std::clamp(value, .2f, 1.f); }
      else if (label == "icon_opacity") {float value;if(row>>value && std::isfinite(value))icon_opacity_=std::clamp(value,.3f,1.f);}
      else if (label == "auto_hide") { int value; if (row >> value) auto_hide_ = value != 0; }
      else if (label == "steering") { std::string value; if (row >> value && (value == "arrows" || value == "stick")) arrow_steering_ = value == "arrows"; }
      else if (label == "steering_buttons") { int value; if (row >> value && (value == 0 || value == 1)) arrow_steering_ = value == 1; }
      else if (label == "steering_response_ms") { int value; if (row >> value) steering_response_ms_ = std::clamp(value, 0, 500); }
      else if (label == "steering_return_ms") { int value; if (row >> value) steering_return_ms_ = std::clamp(value, -1, 500); }
      else if (label == "steering_lock") { float value; if (row >> value && std::isfinite(value)) steering_lock_ = std::clamp(value, .25f, 1.f); }
      else if (label == "analog_pedals") { int value; if (row >> value) analog_pedals_ = value != 0; }
      else if (label == "high_contrast") { int value; if (row >> value) high_contrast_ = value != 0; }
      else if (label == "idle_seconds") {float value;if(row>>value && std::isfinite(value))idle_seconds_=std::clamp(value,0.f,120.f);}
      else if (label == "left_mode") {int value;if(row>>value)left_mode_=std::clamp(value,0,2);}
      else if (label == "right_mode") {int value;if(row>>value)right_mode_=std::clamp(value,0,2);}
      else if (label == "driving_labels") {int value;if(row>>value)driving_icons_=value!=0;}
      else if (label == "button_hints") {int value;if(row>>value)button_hints_=value!=0;}
      else if (label == "map") {
        std::string from,to;if(row>>from>>to){
          const auto find=[&](const std::string& name){for(const auto& c:Controls())if(name==c.label)return c.control;return Control::kNone;};
          const auto valid=[](Control c){return c!=Control::kNone&&c!=Control::kStick&&c!=Control::kRightStick&&c!=Control::kSteerLeft&&c!=Control::kSteerRight;};
          auto source=find(from),target=find(to);if(valid(source)&&valid(target))mapping_[source]=target;
        }
      }
      else if (label == "timeout") {
        std::string name;float value;
        if(row>>name>>value && std::isfinite(value)){
          if(extended && name=="LEFT")name="DPAD_LEFT";
          else if(extended && name=="RIGHT")name="DPAD_RIGHT";
          else if(name=="STEER-L")name="LEFT";
          else if(name=="STEER-R")name="RIGHT";
          for(size_t i=0;i<controls_.size();i++)if(name==controls_[i].label)timeouts_[i]=std::clamp(value,-1.f,120.f);
        }
      }
      else {
        // Extended layouts use LEFT/RIGHT for the D-pad, not steering.
        if (extended && label == "LEFT") label = "DPAD_LEFT";
        else if (extended && label == "RIGHT") label = "DPAD_RIGHT";
        else if (label == "STEER-L") label = "LEFT";
        else if (label == "STEER-R") label = "RIGHT";
        float x, y, r; int hide;
        if (!(row >> x >> y >> r >> hide) || !std::isfinite(x) || !std::isfinite(y) || !std::isfinite(r)) continue;
        for (size_t i = 0; i < controls_.size(); ++i) if (label == controls_[i].label) {
          controls_[i].x = std::clamp(x, 0.f, 1.f); controls_[i].y = std::clamp(y, 0.f, 1.f);
          controls_[i].radius = std::clamp(r, .035f, .24f); hidden_[i] = hide != 0;
        }
      }
    }
  }
  Changed();
}
void TouchPad::SetPhysicalController(bool connected) {
  { std::lock_guard lock(mutex_); if (physical_controller_ == connected) return; physical_controller_ = connected; fingers_.clear(); steering_value_ = 0; steering_updated_ = {}; }
  Changed();
}
void TouchPad::UpdateViewport(float width,float height) {
  std::lock_guard lock(mutex_);
  if(!std::isfinite(width)||!std::isfinite(height)||width<=0||height<=0){fingers_.clear();steering_value_=0;steering_updated_={};return;}
  if(width_>0&&height_>0&&(width_!=width||height_!=height)){
    fingers_.clear();steering_value_=0;steering_updated_={};
  }
  width_=width;height_=height;
}
void TouchPad::SetSafeArea(float left,float top,float right,float bottom) {
  const auto safe=[](float x){return std::isfinite(x)?std::max(0.f,x):0.f;};
  std::lock_guard lock(mutex_);
  left=safe(left);top=safe(top);right=safe(right);bottom=safe(bottom);
  if(left!=safe_left_||top!=safe_top_||right!=safe_right_||bottom!=safe_bottom_){fingers_.clear();steering_value_=0;steering_updated_={};}
  safe_left_=left;safe_top_=top;safe_right_=right;safe_bottom_=bottom;
}
void TouchPad::SetMenuContext(bool menu) {
  {std::lock_guard lock(mutex_);if(menu_context_==menu)return;menu_context_=menu;fingers_.clear();steering_value_=0;steering_updated_={};}
  Changed();
}
bool TouchPad::MenuContext() const {std::lock_guard lock(mutex_);return menu_context_;}
float TouchPad::Opacity() const { std::lock_guard lock(mutex_); return high_contrast_ ? std::max(opacity_, .85f) : opacity_; }
std::string TouchPad::PressedControls() const {
  std::lock_guard lock(mutex_);
  std::string names;
  if(suspended_ || (physical_controller_ && auto_hide_))return names;
  const auto& controls=controls_.empty()?Controls():controls_;
  for(const auto& control:controls){
    if(std::any_of(fingers_.begin(),fingers_.end(),[&](const auto& entry){return entry.second.control==control.control;})){
      if(!names.empty())names+='|';names+=DisplayLabel(control);
    }
  }
  return names;
}
void TouchPad::SetPhoneSteering(float value,bool enabled){
  {std::lock_guard lock(mutex_);phone_enabled_=enabled&&std::isfinite(value);phone_steering_=phone_enabled_?std::clamp(value,-1.f,1.f):0;}
  Changed();
}
void TouchPad::SetRumbleCallback(std::function<void(uint16_t,uint16_t)> callback){std::lock_guard lock(mutex_);rumble_=std::move(callback);}
void TouchPad::Rumble(uint16_t left,uint16_t right){
  std::function<void(uint16_t,uint16_t)> callback;
  {std::lock_guard lock(mutex_);callback=rumble_;if(suspended_)left=right=0;}
  if(callback)callback(left,right);
}

std::unique_ptr<rex::input::InputDriver> TouchPad::CreateDriver() {
  return std::make_unique<TouchPadDriver>(*this);
}

void TouchPad::SetChangedCallback(std::function<void()> callback) {
  std::lock_guard lock(mutex_);
  changed_ = std::move(callback);
}

void TouchPad::SetSuspended(bool suspended) {
  {
    std::lock_guard lock(mutex_);
    if (suspended_ == suspended) {
      return;
    }
    suspended_ = suspended;
    if (suspended) {
      fingers_.clear();  // the menus take over; nothing stays held
      steering_value_ = 0; steering_updated_ = {};
    }
  }
  if(suspended)Rumble(0,0);
  Changed();
}

float TouchPad::Radius(const Layout& control, float width, float height) const {
  const auto* icon=FindTouchIcon(DisplayLabel(control));
  const float w=std::max(1.f,width-safe_left_-safe_right_);
  const float h=std::max(1.f,height-safe_top_-safe_bottom_);
  return std::min({control.radius*height,w*.49f/std::max(1.f,icon?icon->width:1.f),
      h*.49f/std::max(1.f,icon?icon->height:1.f)});
}

void TouchPad::Place(const Layout& control, float width, float height, float& x,
                     float& y) const {
  // The layout's fractions span the safe area, so no control sits under a
  // camera cutout or a rounded corner.
  const float safe_width = std::max(1.0f, width - safe_left_ - safe_right_);
  const float safe_height = std::max(1.0f, height - safe_top_ - safe_bottom_);
  const float r=Radius(control,width,height);
  const auto* icon=FindTouchIcon(DisplayLabel(control));
  const float rx=r*std::max(1.f,icon?icon->width:1.f),ry=r*std::max(1.f,icon?icon->height:1.f);
  x = safe_left_ + std::clamp(control.x * safe_width, rx, safe_width - rx);
  y = safe_top_ + std::clamp(control.y * safe_height, ry, safe_height - ry);
}

TouchPad::Control TouchPad::Hit(float x, float y, float width, float height) const {
  Control best = Control::kNone;
  float best_distance = 0.0f;
  const auto& controls = controls_.empty() ? Controls() : controls_;
  for (size_t i = 0; i < controls.size(); ++i) {
    if ((!hidden_.empty() && hidden_[i] && !(menu_context_&&MenuControl(controls[i].control)&&controls[i].control!=Control::kRightStick&&controls[i].control!=Control::kLeftStickClick&&controls[i].control!=Control::kRightStickClick)) || !Enabled(controls[i].control)) continue;
    const Layout& control = controls[i];
    if((control.control==Control::kStick && left_mode_==2)||(control.control==Control::kRightStick && right_mode_==2))continue;
    float control_x, control_y;
    Place(control, width, height, control_x, control_y);
    const float dx = x - control_x;
    const float dy = y - control_y;
    // A generous target: a thumb lands off-centre.
    const float distance = std::sqrt(dx * dx + dy * dy);
    const auto* icon = FindTouchIcon(DisplayLabel(control));
    const float reach = Radius(control,width,height)*1.15f;
    const bool inside = icon && icon->rectangular
        ? std::abs(dx) <= icon->width * reach && std::abs(dy) <= icon->height * reach
        : distance <= reach;
    if (inside && (best == Control::kNone || distance < best_distance)) {
      best = control.control;
      best_distance = distance;
    }
  }
  if(best==Control::kNone){
    const float safe_width=std::max(1.f,width-safe_left_-safe_right_),safe_height=std::max(1.f,height-safe_top_-safe_bottom_);
    for(size_t i=0;i<controls.size();i++){
      const auto c=controls[i].control;
      if((!hidden_.empty()&&hidden_[i])||!Enabled(c))continue;
      if(y<safe_top_+safe_height*.55f||y>height-safe_bottom_||x<safe_left_||x>width-safe_right_)continue;
      if(c==Control::kStick&&left_mode_==2&&x<safe_left_+safe_width*.45f)return c;
      if(c==Control::kRightStick&&right_mode_==2&&x>safe_left_+safe_width*.55f)return c;
    }
  }
  return best;
}

TouchPad::Control TouchPad::Action(Control source) const {
  const auto it=mapping_.find(source);return menu_context_||it==mapping_.end()?source:it->second;
}
const char* TouchPad::DisplayLabel(const Layout& control) const {
  const auto action=Action(control.control);for(const auto& c:Controls())if(c.control==action)return c.label;return control.label;
}
bool TouchPad::Enabled(Control control) const {
  if(menu_context_)return MenuControl(control);
  if (control == Control::kStick) return !arrow_steering_;
  if (control == Control::kSteerLeft || control == Control::kSteerRight) return arrow_steering_;
  return true;
}
bool TouchPad::MenuControl(Control control) {
  return control!=Control::kNone && control!=Control::kThrottle && control!=Control::kBrake &&
      control!=Control::kSteerLeft && control!=Control::kSteerRight && control!=Control::kStick;
}

bool TouchPad::IsDpad(Control control) {
  return control >= Control::kDpadUp && control <= Control::kDpadRight;
}

void TouchPad::HandleTouch(rex::ui::TouchEvent& e, float width, float height) {
  if (suspended_ || (auto_hide_ && physical_controller_)) return;
  last_touch_ = std::chrono::steady_clock::now();
  const auto remember=[&](Control control){for(size_t i=0;i<controls_.size();i++)if(controls_[i].control==control&&i<activity_.size())activity_[i]=last_touch_;};
  switch (e.action()) {
    case rex::ui::TouchEvent::Action::kDown: {
      Finger finger;
      finger.control = Hit(e.x(), e.y(), width, height);
      finger.arrow = finger.control == Control::kSteerLeft || finger.control == Control::kSteerRight;
      finger.dpad = IsDpad(finger.control);
      if ((finger.control == Control::kStick || finger.control == Control::kRightStick) &&
          std::any_of(fingers_.begin(), fingers_.end(), [&](const auto& held) {
            return held.second.control == finger.control;
          })) finger.control = Control::kNone;
      if (finger.arrow && std::none_of(fingers_.begin(), fingers_.end(), [](const auto& held) { return held.second.arrow; }))
        steering_updated_ = std::chrono::steady_clock::now();
      finger.origin_x = finger.x = e.x();
      finger.origin_y = finger.y = e.y();
      if((finger.control==Control::kStick&&left_mode_==1)||(finger.control==Control::kRightStick&&right_mode_==1)){
        const auto& controls=controls_.empty()?Controls():controls_;
        const auto layout=std::find_if(controls.begin(),controls.end(),[&](const auto& c){return c.control==finger.control;});
        if(layout!=controls.end())Place(*layout,width,height,finger.origin_x,finger.origin_y);
      }
      remember(finger.control);
      fingers_[e.pointer_id()] = finger;
      break;
    }
    case rex::ui::TouchEvent::Action::kMove: {
      auto it = fingers_.find(e.pointer_id());
      if (it != fingers_.end()) {
        auto& finger = it->second;
        remember(finger.control);
        finger.x = e.x(); finger.y = e.y();
        if (finger.arrow) {
          const auto hit = Hit(e.x(), e.y(), width, height);
          finger.control = hit == Control::kSteerLeft || hit == Control::kSteerRight ? hit : Control::kNone;
        } else if (finger.dpad) {
          const auto hit = Hit(e.x(), e.y(), width, height);
          finger.control = IsDpad(hit) ? hit : Control::kNone;
        }
        remember(finger.control);
      }
      break;
    }
    case rex::ui::TouchEvent::Action::kUp:
      if(auto finger=fingers_.find(e.pointer_id());finger!=fingers_.end())remember(finger->second.control);
      fingers_.erase(e.pointer_id());
      // Idle fade starts at release, even after a long stationary hold.
      break;
    case rex::ui::TouchEvent::Action::kCancel:
      fingers_.clear();
      steering_value_ = 0; steering_updated_ = {};
      break;
  }
}

void TouchPad::OnTouchEvent(rex::ui::TouchEvent& e) {
  const auto* window = e.target();
  if (!window) {
    return;
  }
  const float width = float(window->GetActualPhysicalWidth());
  const float height = float(window->GetActualPhysicalHeight());
  if (width <= 0.0f || height <= 0.0f) {
    return;
  }
  UpdateViewport(width,height);
  const auto safe=window->GetSafeAreaInsets();
  SetSafeArea(float(safe.left),float(safe.top),float(safe.right),float(safe.bottom));
  {
    std::lock_guard lock(mutex_);
    HandleTouch(e, width, height);
  }
  Changed();
}

void TouchPad::Changed() {
  std::function<void()> changed;
  {
    std::lock_guard lock(mutex_);
    changed = changed_;
  }
  if (changed) {
    changed();
  }
}

bool TouchPad::Visible() const {
  std::lock_guard lock(mutex_);
  if(suspended_||(auto_hide_&&physical_controller_))return false;
  const auto& controls=controls_.empty()?Controls():controls_;
  for(size_t i=0;i<controls.size();i++)if(Enabled(controls[i].control)&&
      (hidden_.empty()||!hidden_[i]||menu_context_)&&Alpha(i)>0)return true;
  return false;
}

float TouchPad::Alpha(size_t index) const {
  if(menu_context_)return 1.f;
  const auto& controls=controls_.empty()?Controls():controls_;
  if(std::any_of(fingers_.begin(),fingers_.end(),[&](const auto& p){return p.second.control==controls[index].control;}))return 1.f;
  const float timeout=index<timeouts_.size()?timeouts_[index]:-1.f;
  const float seconds=timeout<0?idle_seconds_:timeout;
  if(seconds==0)return 1.f;
  const auto stamp=timeout>=0&&index<activity_.size()&&activity_[index]!=std::chrono::steady_clock::time_point{}?activity_[index]:last_touch_;
  if(stamp==std::chrono::steady_clock::time_point{})return 0.f;
  const float elapsed=std::chrono::duration<float>(std::chrono::steady_clock::now()-stamp).count();
  return std::clamp((seconds-elapsed)/std::min(.75f,seconds),0.f,1.f);
}

rex::input::X_INPUT_GAMEPAD TouchPad::State() const {
  rex::input::X_INPUT_GAMEPAD pad = {};
  std::lock_guard lock(mutex_);
  if (suspended_ || (auto_hide_ && physical_controller_)) {
    return pad;
  }
  uint16_t buttons = 0;
  bool left = false, right = false;
  const auto pedal = [this](const Finger& finger) -> uint8_t {
    if (!analog_pedals_) return 255;
    const auto& controls = controls_.empty() ? Controls() : controls_;
    const auto found = std::find_if(controls.begin(), controls.end(), [&](const auto& c) { return c.control == finger.control; });
    const float travel = std::max(1.f, (found==controls.end()?.1f:found->radius) * height_ * 2.f);
    return uint8_t(std::clamp(.5f + (finger.origin_y - finger.y) / travel, 0.f, 1.f) * 255.f);
  };
  for (const auto& [id, finger] : fingers_) {
    switch (Action(finger.control)) {
      case Control::kStick:
      case Control::kRightStick: {
        const auto& controls = controls_.empty() ? Controls() : controls_;
        const auto stick = std::find_if(controls.begin(), controls.end(), [&](const auto& c) { return c.control == finger.control; });
        const float travel = std::max(1.0f, stick->radius * height_);
        const float x = std::clamp((finger.x - finger.origin_x) / travel, -1.0f, 1.0f);
        const float y = std::clamp((finger.y - finger.origin_y) / travel, -1.0f, 1.0f);
        if (finger.control == Control::kRightStick) {
          pad.thumb_rx = int16_t(x * 32767.0f);
          pad.thumb_ry = int16_t(-y * 32767.0f);
        } else {
          pad.thumb_lx = int16_t(x * 32767.0f);
          pad.thumb_ly = int16_t(-y * 32767.0f);
        }
        break;
      }
      case Control::kThrottle:
        pad.right_trigger = std::max(pad.right_trigger, pedal(finger));
        break;
      case Control::kSteerLeft:
        left = true;
        break;
      case Control::kSteerRight:
        right = true;
        break;
      case Control::kBrake:
        pad.left_trigger = std::max(pad.left_trigger, pedal(finger));
        break;
      case Control::kA:
        buttons |= rex::input::X_INPUT_GAMEPAD_A;
        break;
      case Control::kB:
        buttons |= rex::input::X_INPUT_GAMEPAD_B;
        break;
      case Control::kX:
        buttons |= rex::input::X_INPUT_GAMEPAD_X;
        break;
      case Control::kY:
        buttons |= rex::input::X_INPUT_GAMEPAD_Y;
        break;
      case Control::kLeftBumper:
        buttons |= rex::input::X_INPUT_GAMEPAD_LEFT_SHOULDER;
        break;
      case Control::kRightBumper:
        buttons |= rex::input::X_INPUT_GAMEPAD_RIGHT_SHOULDER;
        break;
      case Control::kBack:
        buttons |= rex::input::X_INPUT_GAMEPAD_BACK;
        break;
      case Control::kStart:
        buttons |= rex::input::X_INPUT_GAMEPAD_START;
        break;
      case Control::kDpadUp:
        buttons |= rex::input::X_INPUT_GAMEPAD_DPAD_UP;
        break;
      case Control::kDpadDown:
        buttons |= rex::input::X_INPUT_GAMEPAD_DPAD_DOWN;
        break;
      case Control::kDpadLeft:
        buttons |= rex::input::X_INPUT_GAMEPAD_DPAD_LEFT;
        break;
      case Control::kDpadRight:
        buttons |= rex::input::X_INPUT_GAMEPAD_DPAD_RIGHT;
        break;
      case Control::kLeftStickClick:
        buttons |= rex::input::X_INPUT_GAMEPAD_LEFT_THUMB;
        break;
      case Control::kRightStickClick:
        buttons |= rex::input::X_INPUT_GAMEPAD_RIGHT_THUMB;
        break;
      case Control::kNone:
        break;
    }
  }
  const auto vertical = rex::input::X_INPUT_GAMEPAD_DPAD_UP | rex::input::X_INPUT_GAMEPAD_DPAD_DOWN;
  const auto horizontal = rex::input::X_INPUT_GAMEPAD_DPAD_LEFT | rex::input::X_INPUT_GAMEPAD_DPAD_RIGHT;
  if ((buttons & vertical) == vertical) buttons &= ~vertical;
  if ((buttons & horizontal) == horizontal) buttons &= ~horizontal;
  pad.buttons = buttons;
  if (arrow_steering_) {
    float target = float(int(right) - int(left)) * steering_lock_;
    if(left&&right)steering_value_=0;
    // Finish centring before starting the opposite turn, so a fast return
    // rate cannot become a fast turn-in on a direction reversal.
    if(target*steering_value_<0 && (steering_response_ms_>0||steering_return_ms_>0))target=0;
    const auto now = std::chrono::steady_clock::now();
    const bool outward=target!=0 && target*steering_value_>=0 && std::abs(target)>std::abs(steering_value_);
    const int response=outward||steering_return_ms_<0?steering_response_ms_:steering_return_ms_;
    if (!response) steering_value_ = target;
    else if (steering_updated_ != std::chrono::steady_clock::time_point{}) {
      const float step = std::max(0.f,std::chrono::duration<float, std::milli>(now - steering_updated_).count()) / response;
      steering_value_ += std::clamp(target - steering_value_, -step, step);
    }
    // Opposite arrows are always neutral, regardless of the response setting.
    if (left && right) steering_value_ = 0;
    steering_updated_ = now;
    pad.thumb_lx = int16_t(std::clamp(steering_value_, -1.f, 1.f) * 32767);
  }
  const bool manual=std::any_of(fingers_.begin(),fingers_.end(),[](const auto& p){return p.second.control==Control::kStick||p.second.control==Control::kSteerLeft||p.second.control==Control::kSteerRight;});
  if(phone_enabled_&&!physical_controller_&&!manual&&!menu_context_)pad.thumb_lx=int16_t(phone_steering_*32767.f);
  return pad;
}

std::vector<TouchPad::Shape> TouchPad::Shapes(float width, float height) const {
  const auto output=State();
  std::vector<Shape> shapes;
  std::lock_guard lock(mutex_);
  const auto& controls = controls_.empty() ? Controls() : controls_;
  for (size_t i = 0; i < controls.size(); ++i) {
    if ((!hidden_.empty() && hidden_[i] && !(menu_context_&&MenuControl(controls[i].control)&&controls[i].control!=Control::kRightStick&&controls[i].control!=Control::kLeftStickClick&&controls[i].control!=Control::kRightStickClick)) || !Enabled(controls[i].control)) continue;
    if((controls[i].control==Control::kStick&&left_mode_==2)||(controls[i].control==Control::kRightStick&&right_mode_==2))continue;
    const Layout& control = controls[i];
    Shape shape;
    Place(control, width, height, shape.x, shape.y);
    shape.radius = Radius(control,width,height);
    shape.label = DisplayLabel(control);
    if(!menu_context_){switch(Action(control.control)){
      case Control::kThrottle:shape.amount=output.right_trigger/255.f;break;
      case Control::kBrake:shape.amount=output.left_trigger/255.f;break;
      case Control::kSteerLeft:shape.amount=std::max(0.f,-output.thumb_lx/32767.f);break;
      case Control::kSteerRight:shape.amount=std::max(0.f,output.thumb_lx/32767.f);break;
      default:break;}}

    shape.opacity=Alpha(i);shape.icon_opacity=icon_opacity_;shape.driving_icons=driving_icons_&&!menu_context_;shape.button_hints=button_hints_;
    shape.stick = control.control == Control::kStick || control.control == Control::kRightStick;
    shape.stick_x = shape.x;
    shape.stick_y = shape.y;
    for (const auto& [id, finger] : fingers_) {
      if (finger.control != control.control) continue;
      shape.pressed = true;
      if (shape.stick) {
        shape.x = finger.origin_x;
        shape.y = finger.origin_y;
        const float dx = finger.x - finger.origin_x, dy = finger.y - finger.origin_y;
        const float length = std::hypot(dx, dy);
        const float scale = length > shape.radius ? shape.radius / length : 1.f;
        shape.stick_x = shape.x + dx * scale;
        shape.stick_y = shape.y + dy * scale;
      }
    }
    shapes.push_back(std::move(shape));
  }
  return shapes;
}

}  // namespace pinyon_shift::ui
