#include "ui/touch_pad.h"
#include "ui/touch_icons.h"
#include <rex/ui/ui_event.h>
#include <cmath>
#include <cstdio>
#include <fstream>
#include <limits>

namespace pinyon_shift::ui {
struct TouchPadTestAccess {
  static void Touch(TouchPad& pad, uint32_t id, rex::ui::TouchEvent::Action action,
                    float x, float y) {
    rex::ui::TouchEvent event(nullptr, id, action, x, y);
    std::lock_guard lock(pad.mutex_);
    pad.width_ = 1280; pad.height_ = 720;
    pad.HandleTouch(event, pad.width_, pad.height_);
  }
  static void Age(TouchPad& pad) {
    std::lock_guard lock(pad.mutex_);
    pad.last_touch_ = std::chrono::steady_clock::now() - std::chrono::seconds(21);
  }
  static void AdvanceSteering(TouchPad& pad, int ms) {
    std::lock_guard lock(pad.mutex_);
    pad.steering_updated_ -= std::chrono::milliseconds(ms);
  }
  static void AgeAll(TouchPad& pad,int seconds){
    std::lock_guard lock(pad.mutex_);
    pad.last_touch_-=std::chrono::seconds(seconds);
    for(auto& stamp:pad.activity_)if(stamp!=std::chrono::steady_clock::time_point{})stamp-=std::chrono::seconds(seconds);
  }
};
}

namespace {
int failures = 0;
#define CHECK(c) do { if (!(c)) { std::fprintf(stderr, "%s:%d: %s\n", __FILE__, __LINE__, #c); ++failures; } } while (false)
using pinyon_shift::ui::TouchPad;
using Access = pinyon_shift::ui::TouchPadTestAccess;
using Action = rex::ui::TouchEvent::Action;
TouchPad::Shape Find(TouchPad& pad, const char* name) {
  for (auto shape : pad.Shapes(1280, 720)) if (shape.label == name) return shape;
  CHECK(false); return {};
}
bool Has(TouchPad& pad, const char* name) {
  for (auto shape : pad.Shapes(1280, 720)) if (shape.label == name) return true;
  return false;
}
void Press(TouchPad& pad, uint32_t id, const char* name) {
  const auto shape = Find(pad, name);
  Access::Touch(pad, id, Action::kDown, shape.x, shape.y);
}
void Release(TouchPad& pad, uint32_t id) { Access::Touch(pad, id, Action::kUp, 0, 0); }
}

int main() {
  auto& pad = TouchPad::Get();
  const auto root = std::filesystem::temp_directory_path() /
      ("fh4droid-touch-" + std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()));
  std::filesystem::create_directories(root);
  const auto layout = root / "layout.txt";
  pad.LoadLayout(layout); pad.SetPhysicalController(false); pad.SetSuspended(false);
  CHECK(Has(pad, "LEFT") && Has(pad, "RIGHT"));
  CHECK(!pad.Visible());
  Press(pad, 1, "LEFT"); CHECK(pad.State().thumb_lx == -32767);
  Press(pad, 2, "RIGHT"); CHECK(pad.State().thumb_lx == 0);
  Press(pad, 3, "LEFT"); CHECK(pad.State().thumb_lx == 0);
  Release(pad, 1); Release(pad, 3); CHECK(pad.State().thumb_lx == 32767);
  Release(pad, 2); CHECK(pad.State().thumb_lx == 0);

  Press(pad, 1, "LEFT");
  auto right = Find(pad, "RIGHT");
  Access::Touch(pad, 1, Action::kMove, right.x, right.y);
  CHECK(pad.State().thumb_lx == 32767);
  Access::Touch(pad, 1, Action::kMove, 640, 360);
  CHECK(pad.State().thumb_lx == 0);
  Access::Touch(pad, 1, Action::kMove, right.x, right.y);
  CHECK(pad.State().thumb_lx == 32767);
  Release(pad, 1);

  Press(pad, 1, "LEFT"); Press(pad, 2, "RT"); Press(pad, 3, "LT"); Press(pad, 4, "A");
  CHECK(pad.State().thumb_lx == -32767 && pad.State().right_trigger == 255 && pad.State().left_trigger == 255);
  CHECK(pad.State().buttons != 0);
  Access::Age(pad); CHECK(pad.Visible());  // A stationary pedal must not fade.
  Access::Touch(pad, 2, Action::kCancel, 0, 0);
  CHECK(pad.State().thumb_lx == 0 && pad.State().right_trigger == 0 && pad.State().buttons == 0);
  Access::Age(pad); CHECK(!pad.Visible());
  Press(pad, 1, "RT"); Access::Age(pad); Release(pad, 1); CHECK(pad.Visible());
  Press(pad, 1, "RT"); pad.SetSuspended(true);
  CHECK(!pad.Visible() && pad.State().right_trigger == 0);
  pad.SetSuspended(false); CHECK(pad.State().right_trigger == 0);
  Press(pad, 1, "LEFT"); pad.SetPhysicalController(true);
  CHECK(!pad.Visible() && pad.State().thumb_lx == 0);
  pad.SetPhysicalController(false); CHECK(pad.State().thumb_lx == 0);

  // Old layouts keep their positions and remain usable with the stick option.
  { std::ofstream out(layout); out << "steering stick\nRT 0.8 0.8 0.1 0\nLS 0.2 0.7 0.15 0\n"; }
  pad.LoadLayout(layout);
  CHECK(!Has(pad, "LEFT") && !Has(pad, "RIGHT"));
  CHECK(std::abs(Find(pad, "RT").x - 1024.f) < 0.01f);
  for (auto shape : pad.Shapes(1280, 720)) if (shape.label == "LS") {
    CHECK(std::abs(shape.radius - 108.f) < .01f);
    Access::Touch(pad, 1, Action::kDown, shape.x, shape.y);
    Access::Touch(pad, 1, Action::kMove, shape.x + shape.radius, shape.y);
    CHECK(pad.State().thumb_lx >= 32766);  // float-to-int endpoint rounding
  }
  Release(pad, 1);
  { std::ofstream out(layout); out << "RT 0.8 0.8 0.1 0\nLS 0.2 0.7 0.15 0\nLEFT 0.1 0.8 0.1 1\n"; }
  pad.LoadLayout(layout);
  CHECK(!Has(pad, "LEFT") && Has(pad, "RIGHT"));
  CHECK(std::abs(Find(pad, "RT").x - 1024.f) < 0.01f);
  { std::ofstream out(layout); out << "auto_hide 0\n"; }
  pad.LoadLayout(layout); pad.SetPhysicalController(true); Press(pad, 1, "RIGHT");
  CHECK(pad.State().thumb_lx == 32767 && pad.Visible());
  pad.SetPhysicalController(false);

  { std::ofstream out(layout); out << "steering_buttons 1\nLEFT 0.6 0.5 0.045 0\nRIGHT 0.7 0.5 0.045 0\nSTEER-L 0.1 0.8 0.08 0\nSTEER-R 0.28 0.8 0.08 0\n"; }
  pad.LoadLayout(layout);
  CHECK(std::abs(Find(pad, "LEFT").x - 128.f) < .01f);
  CHECK(std::abs(Find(pad, "RIGHT").x - 358.4f) < .01f);
  CHECK(std::abs(Find(pad, "DPAD_LEFT").x - 768.f) < .01f);
  CHECK(std::abs(Find(pad, "DPAD_RIGHT").x - 896.f) < .01f);
  Press(pad, 1, "LEFT"); CHECK(pad.State().thumb_lx == -32767); Release(pad, 1);
  { std::ofstream out(layout); out << "steering_buttons 0\n"; }
  pad.LoadLayout(layout); CHECK(Has(pad, "LS") && !Has(pad, "LEFT"));

  { std::ofstream out(layout); out << "steering_response_ms 200\nanalog_pedals 1\nhigh_contrast 1\nopacity 0.2\n"; }
  pad.LoadLayout(layout);
  CHECK(pad.Opacity() >= .85f);
  Press(pad, 1, "RIGHT"); CHECK(pad.State().thumb_lx == 0);
  Access::AdvanceSteering(pad, 50);
  CHECK(pad.State().thumb_lx > 5000 && pad.State().thumb_lx < 20000);
  Access::AdvanceSteering(pad, 250); CHECK(pad.State().thumb_lx == 32767);
  Press(pad, 2, "LEFT"); CHECK(pad.State().thumb_lx == 0);
  Access::Touch(pad, 1, Action::kCancel, 0, 0); CHECK(pad.State().thumb_lx == 0);
  auto gas = Find(pad, "RT");
  Press(pad, 3, "RT"); CHECK(pad.State().right_trigger >= 126 && pad.State().right_trigger <= 128);
  Access::Touch(pad, 3, Action::kMove, gas.x, gas.y - gas.radius);
  CHECK(pad.State().right_trigger >= 254);
  Access::Touch(pad, 3, Action::kMove, gas.x, gas.y + gas.radius);
  CHECK(pad.State().right_trigger <= 1);
  Release(pad, 3); CHECK(pad.State().right_trigger == 0);
  Press(pad, 4, "LT"); CHECK(pad.State().left_trigger >= 126 && pad.State().left_trigger <= 128);
  pad.SetSuspended(true); CHECK(pad.State().left_trigger == 0 && pad.State().thumb_lx == 0);
  pad.SetSuspended(false);

  // The complete controller stays independent of racing steering arrows.
  pad.LoadLayout(root / "default-full-controller.txt");
  CHECK(Has(pad, "RS") && Has(pad, "UP") && Has(pad, "DOWN") &&
        Has(pad, "DPAD_LEFT") && Has(pad, "DPAD_RIGHT") && Has(pad, "L3") && Has(pad, "R3"));
  const struct { const char* name; uint16_t bit; } buttons[] = {
      {"A", rex::input::X_INPUT_GAMEPAD_A}, {"B", rex::input::X_INPUT_GAMEPAD_B},
      {"X", rex::input::X_INPUT_GAMEPAD_X}, {"Y", rex::input::X_INPUT_GAMEPAD_Y},
      {"LB", rex::input::X_INPUT_GAMEPAD_LEFT_SHOULDER},
      {"RB", rex::input::X_INPUT_GAMEPAD_RIGHT_SHOULDER},
      {"BACK", rex::input::X_INPUT_GAMEPAD_BACK}, {"START", rex::input::X_INPUT_GAMEPAD_START},
      {"UP", rex::input::X_INPUT_GAMEPAD_DPAD_UP}, {"DOWN", rex::input::X_INPUT_GAMEPAD_DPAD_DOWN},
      {"DPAD_LEFT", rex::input::X_INPUT_GAMEPAD_DPAD_LEFT},
      {"DPAD_RIGHT", rex::input::X_INPUT_GAMEPAD_DPAD_RIGHT},
      {"L3", rex::input::X_INPUT_GAMEPAD_LEFT_THUMB}, {"R3", rex::input::X_INPUT_GAMEPAD_RIGHT_THUMB},
  };
  for (const auto& button : buttons) {
    Press(pad, 1, button.name);
    CHECK(pad.State().buttons == button.bit && pad.State().thumb_lx == 0 && pad.State().thumb_rx == 0);
    Release(pad, 1); CHECK(pad.State().buttons == 0);
  }
  Press(pad, 1, "UP"); Press(pad, 2, "DPAD_RIGHT"); Press(pad, 3, "LEFT");
  CHECK(pad.State().buttons == (rex::input::X_INPUT_GAMEPAD_DPAD_UP | rex::input::X_INPUT_GAMEPAD_DPAD_RIGHT));
  CHECK(pad.State().thumb_lx == -32767);
  const auto down = Find(pad, "DOWN");
  Access::Touch(pad, 1, Action::kMove, down.x, down.y);
  CHECK(pad.State().buttons == (rex::input::X_INPUT_GAMEPAD_DPAD_DOWN | rex::input::X_INPUT_GAMEPAD_DPAD_RIGHT));
  Press(pad, 4, "UP");
  CHECK(pad.State().buttons == rex::input::X_INPUT_GAMEPAD_DPAD_RIGHT);
  Access::Touch(pad, 1, Action::kMove, 640, 200);
  CHECK(pad.State().buttons == (rex::input::X_INPUT_GAMEPAD_DPAD_UP | rex::input::X_INPUT_GAMEPAD_DPAD_RIGHT));
  Access::Touch(pad, 1, Action::kCancel, 0, 0);
  CHECK(pad.State().buttons == 0 && pad.State().thumb_lx == 0);
  Press(pad, 1, "DPAD_LEFT"); Press(pad, 2, "DPAD_RIGHT");
  CHECK(pad.State().buttons == 0);
  Release(pad, 1); CHECK(pad.State().buttons == rex::input::X_INPUT_GAMEPAD_DPAD_RIGHT);
  Release(pad, 2);

  const auto look = Find(pad, "RS");
  Press(pad, 1, "RS"); Press(pad, 2, "LEFT"); Press(pad, 3, "RT");
  Access::Touch(pad, 1, Action::kMove, look.x + look.radius, look.y - look.radius);
  CHECK(pad.State().thumb_rx == 32767 && pad.State().thumb_ry == 32767);
  CHECK(pad.State().thumb_lx == -32767 && pad.State().right_trigger == 255);
  Press(pad, 4, "RS");  // Another finger cannot steal the held look stick.
  CHECK(pad.State().thumb_rx == 32767 && pad.State().thumb_ry == 32767);
  Release(pad, 4); Release(pad, 1);
  CHECK(pad.State().thumb_rx == 0 && pad.State().thumb_ry == 0);
  CHECK(pad.State().thumb_lx == -32767 && pad.State().right_trigger == 255);
  pad.SetSuspended(true); CHECK(pad.State().thumb_lx == 0 && pad.State().right_trigger == 0);
  pad.SetSuspended(false);

  // Every active control has drawable artwork and prompt hints without fonts.
  for (const auto& shape : pad.Shapes(1280, 720)) {
    const auto* icon = pinyon_shift::ui::FindTouchIcon(shape.label);
    CHECK(icon && !icon->lines.empty());
    if (icon) for (auto paths : {icon->lines, icon->hints, icon->classic}) for (const auto& line : paths) {
      CHECK(std::isfinite(line.x0) && std::isfinite(line.y0) && std::isfinite(line.x1) && std::isfinite(line.y1));
      CHECK(std::abs(line.x0) < icon->width && std::abs(line.x1) < icon->width);
      CHECK(std::abs(line.y0) < icon->height && std::abs(line.y1) < icon->height);
      if (!icon->rectangular) {
        CHECK(std::hypot(line.x0 / icon->width, line.y0 / icon->height) < .98f);
        CHECK(std::hypot(line.x1 / icon->width, line.y1 / icon->height) < .98f);
      }
    }
  }
  // Independent visibility timers never hide a held button.
  {std::ofstream out(layout);out<<"idle_seconds 0\ntimeout Y 1\ndriving_labels 0\nbutton_hints 0\n";}
  pad.LoadLayout(layout);Press(pad,1,"Y");Release(pad,1);Access::AgeAll(pad,2);
  CHECK(pad.Visible()&&Find(pad,"Y").opacity==0&&!Find(pad,"A").driving_icons);
  Press(pad,1,"Y");Access::AgeAll(pad,2);CHECK(Find(pad,"Y").opacity==1&&pad.State().buttons==rex::input::X_INPUT_GAMEPAD_Y);Release(pad,1);
  CHECK(Find(pad,"Y").opacity>.9f);

  // Fixed sticks use the saved centre; floating sticks start at the touch.
  {std::ofstream out(layout);out<<"steering stick\nleft_mode 1\nidle_seconds 0\n";}
  pad.LoadLayout(layout);const auto fixed=Find(pad,"LS");
  Access::Touch(pad,1,Action::kDown,fixed.x+fixed.radius*.5f,fixed.y);
  CHECK(pad.State().thumb_lx>16000&&pad.State().thumb_lx<17000);Release(pad,1);
  {std::ofstream out(layout);out<<"steering stick\nleft_mode 0\n";}
  pad.LoadLayout(layout);Access::Touch(pad,1,Action::kDown,fixed.x+fixed.radius*.5f,fixed.y);
  CHECK(pad.State().thumb_lx==0);Release(pad,1);

  // Invisible steering/look zones respect button precedence and axis ownership.
  {std::ofstream out(layout);out<<"steering stick\nleft_mode 2\nright_mode 2\n";}
  pad.LoadLayout(layout);CHECK(!Has(pad,"LS")&&!Has(pad,"RS"));
  Access::Touch(pad,1,Action::kDown,512,662);Access::Touch(pad,1,Action::kMove,612,662);
  Access::Touch(pad,2,Action::kDown,832,684);Access::Touch(pad,2,Action::kMove,832,584);
  CHECK(pad.State().thumb_lx==32767&&pad.State().thumb_ry==32767);
  Release(pad,1);Release(pad,2);Press(pad,3,"RT");CHECK(pad.State().right_trigger==255&&pad.State().thumb_rx==0);Release(pad,3);
  pad.SetPhoneSteering(.7f,true);CHECK(pad.State().thumb_lx>22000);
  Access::Touch(pad,1,Action::kDown,512,662);CHECK(pad.State().thumb_lx==0);Release(pad,1);
  pad.SetPhysicalController(true);CHECK(pad.State().thumb_lx==0);pad.SetPhysicalController(false);
  pad.SetSuspended(true);CHECK(pad.State().thumb_lx==0);pad.SetSuspended(false);
  pad.SetPhoneSteering(std::numeric_limits<float>::quiet_NaN(),true);CHECK(pad.State().thumb_lx==0);
  uint16_t rumbleLeft=0,rumbleRight=0;
  pad.SetRumbleCallback([&](uint16_t l,uint16_t r){rumbleLeft=l;rumbleRight=r;});
  pad.Rumble(123,456);CHECK(rumbleLeft==123&&rumbleRight==456);
  pad.SetSuspended(true);CHECK(rumbleLeft==0&&rumbleRight==0);pad.SetSuspended(false);pad.SetRumbleCallback({});
  std::filesystem::remove_all(root);
  std::printf("Touch controls: %d failures\n", failures);
  return failures ? 1 : 0;
}
