#include "android/native/root_settings.h"

#include <cassert>
#include <iostream>

int main() {
  using namespace pinyon_shift::android_config;
  const std::string before =
      "\xef\xbb\xbf# keep this header\r\n"
      "\"café\" = 12 # keep this comment\r\n"
      "notes = \"\"\"\r\nfh1_render_scale_percent = 100\r\n\"\"\"\r\n"
      "vsync = true\r\n"
      "[custom]\r\n"
      "vsync = true\r\n"
      "fh1_render_scale_percent = 100\r\n";
  const auto updated = Update(before, "vsync = false\nfh1_render_scale_percent = 80\n\"café\" = 13\n");
  const auto tree = toml::parse(updated);
  assert(tree["vsync"].value<bool>() == false);
  assert(tree["fh1_render_scale_percent"].value<int>() == 80);
  assert(tree["café"].value<int>() == 13);
  assert(tree["custom"]["vsync"].value<bool>() == true);
  assert(tree["custom"]["fh1_render_scale_percent"].value<int>() == 100);
  assert(updated.starts_with("\xef\xbb\xbf# keep this header\r\n"));
  assert(updated.find("\"café\" = 13 # keep this comment\r\n") != std::string::npos);
  assert(updated.find("notes = \"\"\"\r\nfh1_render_scale_percent = 100\r\n\"\"\"\r\n") != std::string::npos);
  const auto settings = Read(updated);
  assert(settings["vsync"].value<std::string>() == "false");
  assert(settings["fh1_render_scale_percent"].value<std::string>() == "80");
  assert(!settings.contains("custom"));

  auto restored = Restore(updated, before, "fh1_render_scale_percent");
  assert(!toml::parse(restored).contains("fh1_render_scale_percent"));
  assert(restored.find("[custom]\r\nvsync = true\r\nfh1_render_scale_percent = 100") != std::string::npos);
  assert(toml::parse(Restore(restored, before, "vsync"))["vsync"].value<bool>() == true);
  assert(Restore("value = 1\nkeep = true\n", "", "value") == "keep = true\n");
  assert(Update("# c", "vsync = false # new setting\n") == "# c\nvsync = false # new setting\n");
  assert(Update("vsync = true", "vsync = false\n") == "vsync = false");
  assert(Update("# heading\n", "vsync = false\n") == "# heading\nvsync = false\n");
  assert(Update("", "vsync = false\n") == "vsync = false\n");
  std::cout << "PASS: Android settings edit and restore address top-level TOML keys\n";
}
