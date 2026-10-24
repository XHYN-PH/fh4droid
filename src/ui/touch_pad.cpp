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
constexpr auto kVisibleAfterTouch = std::chrono::seconds(20);
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
      out->gamepad.buttons = UINT16_MAX;
      out->gamepad.left_trigger = UINT8_MAX;
      out->gamepad.right_trigger = UINT8_MAX;
      out->gamepad.thumb_lx = INT16_MAX;
      out->gamepad.thumb_ly = INT16_MAX;
    }
    return X_ERROR_SUCCESS;
  }
  X_RESULT SetDeviceVibration(DeviceId id, rex::input::X_INPUT_VIBRATION*) override {
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
  };
  return controls;
}

void TouchPad::LoadLayout(const std::filesystem::path& path) {
  {
    std::lock_guard lock(mutex_);
    controls_ = Controls(); hidden_.assign(controls_.size(), false);
    opacity_ = 0.55f; auto_hide_ = true; arrow_steering_ = true; fingers_.clear();
    analog_pedals_ = false; high_contrast_ = false; steering_response_ms_ = 0;
    steering_value_ = 0; steering_updated_ = {};
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
      else if (label == "auto_hide") { int value; if (row >> value) auto_hide_ = value != 0; }
      else if (label == "steering") { std::string value; if (row >> value && (value == "arrows" || value == "stick")) arrow_steering_ = value == "arrows"; }
      else if (label == "steering_buttons") { int value; if (row >> value && (value == 0 || value == 1)) arrow_steering_ = value == 1; }
      else if (label == "steering_response_ms") { int value; if (row >> value) steering_response_ms_ = std::clamp(value, 0, 500); }
      else if (label == "analog_pedals") { int value; if (row >> value) analog_pedals_ = value != 0; }
      else if (label == "high_contrast") { int value; if (row >> value) high_contrast_ = value != 0; }
      else {
        // Extended layouts use LEFT/RIGHT for the D-pad, not steering.
        if (extended && (label == "LEFT" || label == "RIGHT")) continue;
        if (label == "STEER-L") label = "LEFT";
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
float TouchPad::Opacity() const { std::lock_guard lock(mutex_); return high_contrast_ ? std::max(opacity_, .85f) : opacity_; }

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
  Changed();
}

void TouchPad::Place(const Layout& control, float width, float height, float& x,
                     float& y) const {
  // The layout's fractions span the safe area, so no control sits under a
  // camera cutout or a rounded corner.
  const float safe_width = std::max(1.0f, width - safe_left_ - safe_right_);
  const float safe_height = std::max(1.0f, height - safe_top_ - safe_bottom_);
  const float r = std::min(control.radius * height, std::min(safe_width, safe_height) * .49f);
  x = safe_left_ + std::clamp(control.x * safe_width, r, safe_width - r);
  y = safe_top_ + std::clamp(control.y * safe_height, r, safe_height - r);
}

TouchPad::Control TouchPad::Hit(float x, float y, float width, float height) const {
  Control best = Control::kNone;
  float best_distance = 0.0f;
  const auto& controls = controls_.empty() ? Controls() : controls_;
  for (size_t i = 0; i < controls.size(); ++i) {
    if ((!hidden_.empty() && hidden_[i]) || !Enabled(controls[i].control)) continue;
    const Layout& control = controls[i];
    float control_x, control_y;
    Place(control, width, height, control_x, control_y);
    const float dx = x - control_x;
    const float dy = y - control_y;
    // A generous target: a thumb lands off-centre.
    const float distance = std::sqrt(dx * dx + dy * dy);
    const auto* icon = FindTouchIcon(control.label);
    const float reach = control.radius * height * 1.15f;
    const bool inside = icon && icon->rectangular
        ? std::abs(dx) <= icon->width * reach && std::abs(dy) <= icon->height * reach
        : distance <= reach;
    if (inside && (best == Control::kNone || distance < best_distance)) {
      best = control.control;
      best_distance = distance;
    }
  }
  return best;
}

bool TouchPad::Enabled(Control control) const {
  if (control == Control::kStick) return !arrow_steering_;
  if (control == Control::kSteerLeft || control == Control::kSteerRight) return arrow_steering_;
  return true;
}

void TouchPad::HandleTouch(rex::ui::TouchEvent& e, float width, float height) {
  if (suspended_ || (auto_hide_ && physical_controller_)) return;
  last_touch_ = std::chrono::steady_clock::now();
  switch (e.action()) {
    case rex::ui::TouchEvent::Action::kDown: {
      Finger finger;
      finger.control = Hit(e.x(), e.y(), width, height);
      finger.arrow = finger.control == Control::kSteerLeft || finger.control == Control::kSteerRight;
      if (finger.arrow && std::none_of(fingers_.begin(), fingers_.end(), [](const auto& held) { return held.second.arrow; }))
        steering_updated_ = std::chrono::steady_clock::now();
      finger.origin_x = finger.x = e.x();
      finger.origin_y = finger.y = e.y();
      fingers_[e.pointer_id()] = finger;
      break;
    }
    case rex::ui::TouchEvent::Action::kMove: {
      auto it = fingers_.find(e.pointer_id());
      if (it != fingers_.end()) {
        auto& finger = it->second;
        finger.x = e.x(); finger.y = e.y();
        if (finger.arrow) {
          const auto hit = Hit(e.x(), e.y(), width, height);
          finger.control = hit == Control::kSteerLeft || hit == Control::kSteerRight ? hit : Control::kNone;
        }
      }
      break;
    }
    case rex::ui::TouchEvent::Action::kUp:
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
  {
    std::lock_guard lock(mutex_);
    width_ = width;
    height_ = height;
    const auto insets = window->GetSafeAreaInsets();
    if (float(insets.left) != safe_left_ || float(insets.top) != safe_top_ ||
        float(insets.right) != safe_right_ || float(insets.bottom) != safe_bottom_) {
      REXLOG_INFO("Touch controls: safe area insets left {} top {} right {} bottom {}",
                  insets.left, insets.top, insets.right, insets.bottom);
    }
    safe_left_ = float(insets.left);
    safe_top_ = float(insets.top);
    safe_right_ = float(insets.right);
    safe_bottom_ = float(insets.bottom);
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
  return !suspended_ && !(auto_hide_ && physical_controller_) &&
         last_touch_ != std::chrono::steady_clock::time_point{} &&
         (!fingers_.empty() || std::chrono::steady_clock::now() - last_touch_ < kVisibleAfterTouch);
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
    const float travel = std::max(1.f, found->radius * height_ * 2.f);
    return uint8_t(std::clamp(.5f + (finger.origin_y - finger.y) / travel, 0.f, 1.f) * 255.f);
  };
  for (const auto& [id, finger] : fingers_) {
    switch (finger.control) {
      case Control::kStick: {
        const auto& controls = controls_.empty() ? Controls() : controls_;
        const auto stick = std::find_if(controls.begin(), controls.end(), [](const auto& c) { return c.control == Control::kStick; });
        const float travel = std::max(1.0f, stick->radius * height_);
        const float x = std::clamp((finger.x - finger.origin_x) / travel, -1.0f, 1.0f);
        const float y = std::clamp((finger.y - finger.origin_y) / travel, -1.0f, 1.0f);
        pad.thumb_lx = int16_t(x * 32767.0f);
        pad.thumb_ly = int16_t(-y * 32767.0f);
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
      case Control::kNone:
        break;
    }
  }
  pad.buttons = buttons;
  if (arrow_steering_) {
    const float target = float(int(right) - int(left));
    const auto now = std::chrono::steady_clock::now();
    if (!steering_response_ms_) steering_value_ = target;
    else if (steering_updated_ != std::chrono::steady_clock::time_point{}) {
      const float step = std::chrono::duration<float, std::milli>(now - steering_updated_).count() / steering_response_ms_;
      steering_value_ += std::clamp(target - steering_value_, -step, step);
    }
    // Opposite arrows are always neutral, regardless of the response setting.
    if (left && right) steering_value_ = 0;
    steering_updated_ = now;
    pad.thumb_lx = int16_t(std::clamp(steering_value_, -1.f, 1.f) * 32767);
  }
  return pad;
}

std::vector<TouchPad::Shape> TouchPad::Shapes(float width, float height) const {
  std::vector<Shape> shapes;
  std::lock_guard lock(mutex_);
  const auto& controls = controls_.empty() ? Controls() : controls_;
  for (size_t i = 0; i < controls.size(); ++i) {
    if ((!hidden_.empty() && hidden_[i]) || !Enabled(controls[i].control) || controls[i].control == Control::kStick) continue;
    const Layout& control = controls[i];
    Shape shape;
    Place(control, width, height, shape.x, shape.y);
    shape.radius = control.radius * height;
    shape.label = control.label;
    for (const auto& [id, finger] : fingers_) {
      shape.pressed |= finger.control == control.control;
    }
    shapes.push_back(std::move(shape));
  }
  // The stick where the finger holds it, or its resting place.
  Shape stick;
  stick.stick = true;
  const auto layout = std::find_if(controls.begin(), controls.end(), [](const auto& c) { return c.control == Control::kStick; });
  const auto index = size_t(layout - controls.begin());
  if (arrow_steering_ || (!hidden_.empty() && hidden_[index])) return shapes;
  Place(*layout, width, height, stick.x, stick.y);
  stick.radius = layout->radius * height;
  stick.label = layout->label;
  stick.stick_x = stick.x;
  stick.stick_y = stick.y;
  for (const auto& [id, finger] : fingers_) {
    if (finger.control == Control::kStick) {
      stick.pressed = true;
      stick.x = finger.origin_x;
      stick.y = finger.origin_y;
      const float dx = finger.x - finger.origin_x, dy = finger.y - finger.origin_y;
      const float length = std::sqrt(dx * dx + dy * dy);
      const float scale = length > stick.radius ? stick.radius / length : 1.0f;
      stick.stick_x = stick.x + dx * scale;
      stick.stick_y = stick.y + dy * scale;
    }
  }
  shapes.push_back(std::move(stick));
  return shapes;
}

}  // namespace pinyon_shift::ui
